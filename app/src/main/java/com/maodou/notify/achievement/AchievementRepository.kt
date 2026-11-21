package com.maodou.notify.achievement

import com.maodou.notify.data.AchievementCategoryEntity
import com.maodou.notify.data.AchievementDao
import com.maodou.notify.data.AchievementDefEntity
import com.maodou.notify.data.AppDatabase
import com.maodou.notify.data.AchievementRewardClaimEntity
import com.maodou.notify.data.Result
import com.maodou.notify.util.Prefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 成就系统唯一对外入口：配置下发、事件接入、进度查询、解锁判定、奖励领取。
 *
 * 并发策略：
 * 1) 进程内 Mutex 串行化 onEvent / reconcile / claim；
 * 2) Room @Transaction 把「去重 + 读进度 + 推进 + 解锁」放在单个事务；
 * 3) eventKey 唯一索引与 claimKey 唯一索引提供跨进程/跨调用的幂等兜底。
 */
class AchievementRepository(
    db: AppDatabase,
    private val prefs: Prefs
) {
    private val dao: AchievementDao = db.achievementDao()
    private val notificationDao = db.notificationDao()
    private val ruleDao = db.ruleDao()
    private val mutex = Mutex()

    /** 首次安装写入内置配置；INSERT OR IGNORE 保证不覆盖已有配置与进度。 */
    suspend fun seedDefaults() {
        AchievementCatalog.categories().forEach { dao.insertCategory(it) }
        AchievementCatalog.definitions().forEach { dao.insertDefinition(it) }
    }

    /** 外部配置入口：新增/覆盖成就定义，无需改表或发版。 */
    suspend fun installDefinitions(
        categories: List<AchievementCategoryEntity>,
        definitions: List<AchievementDefEntity>
    ) {
        categories.forEach { dao.insertCategory(it) }
        definitions.forEach { dao.replaceDefinition(it) }
        definitions.forEach { dao.syncTarget(it.id, ProgressSpec.fromJson(it.progressJson).target) }
    }

    fun observeCategories(): Flow<List<AchievementCategoryEntity>> = dao.observeCategories()

    /** 成就列表数据源。categoryId=null 表示全部分类。 */
    fun observeAchievements(categoryId: String? = null): Flow<List<AchievementView>> =
        combine(dao.observeDefinitions(), dao.observeProgress(), dao.observeClaims()) { defs, progresses, claims ->
            val pMap = progresses.associateBy { it.defId }
            val cMap = claims.associateBy { it.defId }
            defs.filter { categoryId == null || it.categoryId == categoryId }
                .map { def -> buildView(def, pMap[def.id], cMap[def.id]) }
        }

    suspend fun getAchievement(defId: String): AchievementView? {
        val def = dao.getDefinition(defId) ?: return null
        return buildView(def, dao.getProgress(defId), dao.getClaim(defId))
    }

    /** 进度查询接口：只返回快照，不泄露 Room 实体。 */
    suspend fun getProgress(defId: String): ProgressSnapshot? {
        val p = dao.getProgress(defId) ?: return null
        val claim = dao.getClaim(defId)
        return ProgressSnapshot(
            defId = p.defId,
            current = p.current,
            target = p.target,
            status = p.status,
            unlockedAt = p.unlockedAt,
            rewardClaimedAt = claim?.claimedAt,
            percent = if (p.target <= 0) 0 else ((p.current * 100) / p.target).toInt().coerceIn(0, 100)
        )
    }

    suspend fun unlockedCount(): Int = dao.unlockedCount()

    /**
     * 事件接入。返回本次**新解锁**的成就（含级联解锁），调用方可据此发通知/埋点。
     * 条件不匹配或绝对值模式的事件会被跳过，不会消费 eventKey。
     */
    suspend fun onEvent(event: AchievementEvent): List<AchievementView> = mutex.withLock {
        val defs = dao.enabledDefinitions()
        val allUnlocked = mutableListOf<AchievementView>()
        var queue = listOf(event)
        var depth = 0
        while (queue.isNotEmpty() && depth < MAX_CASCADE_DEPTH) {
            val next = mutableListOf<AchievementEvent>()
            for (e in queue) {
                for (def in defs) {
                    val condition = ConditionSpec.fromJson(def.conditionJson)
                    if (!matches(e, condition)) continue
                    val spec = ProgressSpec.fromJson(def.progressJson)
                    if (spec.mode == ProgressMode.ABSOLUTE) continue
                    dao.syncTarget(def.id, spec.target)
                    val result = dao.apply(
                        defId = def.id,
                        target = spec.target,
                        eventKey = e.key,
                        eventType = e.type.name,
                        mode = spec.mode,
                        step = spec.step,
                        monotonic = spec.monotonic,
                        now = System.currentTimeMillis()
                    )
                    if (result.unlocked) {
                        getAchievement(def.id)?.let { allUnlocked.add(it) }
                        next.add(AchievementEvent("unlock:${def.id}", EventType.ACHIEVEMENT_UNLOCKED))
                    }
                }
            }
            queue = next
            depth++
        }
        allUnlocked
    }

    /**
     * 从真实数据源校准绝对值进度并补判解锁。
     * 建议在 App 启动、成就页可见、以及关键数据变更后调用，
     * 用于兜住事件漏投 / 数据被清理 / 老用户升级等情况。
     */
    suspend fun reconcile() = mutex.withLock {
        val defs = dao.enabledDefinitions()
        val now = System.currentTimeMillis()
        val newly = mutableListOf<AchievementView>()
        for (def in defs) {
            val spec = ProgressSpec.fromJson(def.progressJson)
            if (spec.mode != ProgressMode.ABSOLUTE) continue
            dao.ensureProgress(def.id, spec.target, now)
            dao.syncTarget(def.id, spec.target)
            val value = absoluteValue(spec.source)
            dao.raiseAbsolute(def.id, value, now)
            if (dao.finalizeUnlock(def.id, now)) {
                getAchievement(def.id)?.let { newly.add(it) }
            }
        }
        var queue = newly.map { AchievementEvent("unlock:${it.defId}", EventType.ACHIEVEMENT_UNLOCKED) }
        var depth = 0
        while (queue.isNotEmpty() && depth < MAX_CASCADE_DEPTH) {
            val next = mutableListOf<AchievementEvent>()
            for (e in queue) {
                for (def in defs) {
                    val condition = ConditionSpec.fromJson(def.conditionJson)
                    if (!matches(e, condition)) continue
                    val spec = ProgressSpec.fromJson(def.progressJson)
                    if (spec.mode == ProgressMode.ABSOLUTE || spec.mode == ProgressMode.ONCE) continue
                    val result = dao.apply(
                        defId = def.id, target = spec.target, eventKey = e.key,
                        eventType = e.type.name, mode = spec.mode, step = spec.step,
                        monotonic = spec.monotonic, now = System.currentTimeMillis()
                    )
                    if (result.unlocked) {
                        getAchievement(def.id)?.let { newly.add(it) }
                        next.add(AchievementEvent("unlock:${def.id}", EventType.ACHIEVEMENT_UNLOCKED))
                    }
                }
            }
            queue = next
            depth++
        }
        newly
    }

    /**
     * 奖励领取。未解锁不可领；无奖励返回 NOT_APPLICABLE；
     * 同一 claimKey 或同一成就重复领取返回 ALREADY_CLAIMED，不产生第二条记录。
     */
    suspend fun claimReward(defId: String, claimKey: String): ClaimResult = mutex.withLock {
        val def = dao.getDefinition(defId) ?: return@withLock ClaimResult.NOT_FOUND
        val reward = RewardSpec.fromJson(def.rewardJson)
        if (reward.type == RewardType.NONE) return@withLock ClaimResult.NOT_APPLICABLE
        val progress = dao.getProgress(defId)
        if (progress == null || progress.status != AchievementStatus.UNLOCKED) {
            return@withLock ClaimResult.LOCKED
        }
        dao.getClaim(defId)?.let { return@withLock ClaimResult.ALREADY_CLAIMED }
        val row = AchievementRewardClaimEntity(defId, claimKey, System.currentTimeMillis())
        val inserted = dao.insertClaim(row)
        if (inserted < 0L) {
            // 主键冲突=并发领取；claimKey 冲突=该 key 已被别的成就使用
            return@withLock if (dao.getClaim(defId) != null) ClaimResult.ALREADY_CLAIMED else ClaimResult.KEY_CONFLICT
        }
        ClaimResult.CLAIMED
    }

    /**
     * 显式进度回退（管理/配置场景）。已解锁状态不会回退——成就不可撤回。
     * 回退本身记入事件账本，便于排查重复触发与异常配置。
     */
    suspend fun resetProgress(defId: String, reason: String = "manual") = mutex.withLock {
        val def = dao.getDefinition(defId) ?: return@withLock
        dao.setCurrent(defId, 0L, System.currentTimeMillis())
        dao.markEvent(
            key = "reset:$defId:$reason:${UUID.randomUUID()}",
            defId = defId,
            type = "RESET",
            now = System.currentTimeMillis()
        )
    }

    private suspend fun absoluteValue(source: ProgressSource): Long = when (source) {
        ProgressSource.NOTIFICATIONS -> notificationDao.countAll().toLong()
        ProgressSource.FILTERED ->
            (notificationDao.countByResult(Result.RULE) + notificationDao.countByResult(Result.AI)).toLong()
        ProgressSource.LEARNED -> notificationDao.countLearned().toLong()
        ProgressSource.RULES -> ruleDao.countAll().toLong()
        ProgressSource.PRO -> if (prefs.proActivated) 1L else 0L
        ProgressSource.UNLOCKS -> dao.unlockedCount().toLong()
        ProgressSource.NONE -> 0L
    }

    private fun matches(event: AchievementEvent, c: ConditionSpec): Boolean = when (c.type) {
        ConditionType.ANY_NOTIFICATION -> event.type == EventType.NOTIFICATION
        ConditionType.FILTERED -> event.type == EventType.NOTIFICATION &&
            if (c.result == "*") event.result != null && event.result != Result.KEPT
            else event.result == c.result
        ConditionType.CATEGORY -> event.type == EventType.NOTIFICATION && event.category == c.category
        ConditionType.PACKAGE -> event.type == EventType.NOTIFICATION && event.packageName == c.packageName
        ConditionType.LEARNED -> event.type == EventType.LEARNED
        ConditionType.RULE_CREATED -> event.type == EventType.RULE_CREATED
        ConditionType.PRO_ACTIVATED -> event.type == EventType.PRO_ACTIVATED
        ConditionType.APP_OPENED -> event.type == EventType.APP_OPENED
        ConditionType.ACHIEVEMENT_UNLOCKED -> event.type == EventType.ACHIEVEMENT_UNLOCKED
    }

    private fun buildView(
        def: AchievementDefEntity,
        progress: com.maodou.notify.data.AchievementProgressEntity?,
        claim: AchievementRewardClaimEntity?
    ): AchievementView {
        val spec = ProgressSpec.fromJson(def.progressJson)
        return AchievementView(
            defId = def.id,
            categoryId = def.categoryId,
            name = def.name,
            description = def.description,
            iconKey = def.iconKey,
            current = progress?.current ?: 0L,
            target = progress?.target ?: spec.target,
            status = progress?.status ?: AchievementStatus.LOCKED,
            unlockedAt = progress?.unlockedAt,
            rewardClaimedAt = claim?.claimedAt,
            reward = RewardSpec.fromJson(def.rewardJson),
            condition = ConditionSpec.fromJson(def.conditionJson),
            sortOrder = def.sortOrder
        )
    }

    companion object {
        private const val MAX_CASCADE_DEPTH = 8
    }
}
