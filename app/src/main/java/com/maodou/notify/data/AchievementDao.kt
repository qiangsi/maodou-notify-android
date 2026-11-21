package com.maodou.notify.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.maodou.notify.achievement.ApplyOutcome
import com.maodou.notify.achievement.ApplyResult
import com.maodou.notify.achievement.AchievementStatus
import com.maodou.notify.achievement.ProgressMode
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievement_categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<AchievementCategoryEntity>>

    @Query("SELECT * FROM achievement_defs WHERE enabled = 1 ORDER BY sortOrder ASC")
    fun observeDefinitions(): Flow<List<AchievementDefEntity>>

    @Query("SELECT * FROM achievement_defs WHERE enabled = 1 ORDER BY sortOrder ASC")
    suspend fun enabledDefinitions(): List<AchievementDefEntity>

    @Query("SELECT * FROM achievement_defs WHERE id = :id")
    suspend fun getDefinition(id: String): AchievementDefEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(item: AchievementCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefinition(item: AchievementDefEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceDefinition(item: AchievementDefEntity)

    @Query("SELECT * FROM achievement_progress")
    fun observeProgress(): Flow<List<AchievementProgressEntity>>

    @Query("SELECT * FROM achievement_progress WHERE defId = :id")
    suspend fun getProgress(id: String): AchievementProgressEntity?

    @Query("INSERT OR IGNORE INTO achievement_progress(defId, current, target, status, updatedAt) VALUES(:defId, 0, :target, 0, :now)")
    suspend fun ensureProgress(defId: String, target: Long, now: Long)

    /** 配置变更时同步目标值；只改 target，不回退 current，也不撤回已解锁状态。 */
    @Query("UPDATE achievement_progress SET target = :target WHERE defId = :defId AND target != :target")
    suspend fun syncTarget(defId: String, target: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(item: AchievementProgressEntity)

    /** 事件去重：返回受影响行数，0 表示同一事件已经消费过。 */
    @Query("INSERT OR IGNORE INTO achievement_events(eventKey, defId, type, processedAt) VALUES(:key, :defId, :type, :now)")
    suspend fun markEvent(key: String, defId: String, type: String, now: Long): Long

    /**
     * 单条成就的「去重 + 读取 + 推进 + 解锁」在一个 SQLite 事务里完成。
     * 事务保证并发调用串行化；eventKey 唯一索引保证跨调用幂等。
     */
    @Transaction
    suspend fun apply(
        defId: String,
        target: Long,
        eventKey: String,
        eventType: String,
        mode: ProgressMode,
        step: Long,
        monotonic: Boolean,
        now: Long
    ): ApplyResult {
        // executeInsert() 返回 rowid；INSERT OR IGNORE 命中唯一键时返回 -1
        if (markEvent(eventKey, defId, eventType, now) <= 0L) {
            return ApplyResult(ApplyOutcome.DUPLICATE, 0L, false)
        }
        ensureProgress(defId, target, now)
        val p = getProgress(defId) ?: return ApplyResult(ApplyOutcome.NO_CHANGE, 0L, false)

        val computed = when (mode) {
            ProgressMode.ONCE -> 1L
            ProgressMode.INCREMENT -> p.current + step.coerceAtLeast(1L)
            ProgressMode.ABSOLUTE -> p.current // 绝对值模式由 reconcile 校准，事件不推进
        }
        val next = (if (monotonic) maxOf(p.current, computed) else computed)
            .coerceIn(0L, p.target)
        if (next == p.current) return ApplyResult(ApplyOutcome.NO_CHANGE, p.current, false)

        saveProgress(p.copy(current = next, updatedAt = now))
        if (next >= p.target && p.status == AchievementStatus.LOCKED) {
            saveProgress(p.copy(
                current = next, status = AchievementStatus.UNLOCKED,
                unlockedAt = now, updatedAt = now
            ))
            return ApplyResult(ApplyOutcome.UNLOCKED, next, true)
        }
        return ApplyResult(ApplyOutcome.ADVANCED, next, false)
    }

    /** 绝对值校准：只升不降，天然抗乱序与重复对账。 */
    @Query("UPDATE achievement_progress SET current = MAX(current, :value), updatedAt = :now WHERE defId = :defId AND current < :value")
    suspend fun raiseAbsolute(defId: String, value: Long, now: Long)

    /** 校准后判定解锁，同样在事务内避免竞态。 */
    @Transaction
    suspend fun finalizeUnlock(defId: String, now: Long): Boolean {
        val p = getProgress(defId) ?: return false
        if (p.status != AchievementStatus.LOCKED || p.current < p.target) return false
        saveProgress(p.copy(status = AchievementStatus.UNLOCKED, unlockedAt = now, updatedAt = now))
        return true
    }

    @Query("SELECT * FROM achievement_reward_claims")
    fun observeClaims(): Flow<List<AchievementRewardClaimEntity>>

    @Query("SELECT * FROM achievement_reward_claims WHERE defId = :defId")
    suspend fun getClaim(defId: String): AchievementRewardClaimEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClaim(item: AchievementRewardClaimEntity): Long

    @Query("SELECT COUNT(*) FROM achievement_progress WHERE status = 1")
    suspend fun unlockedCount(): Int

    /** 显式进度回退，不动 status —— 已解锁成就不可撤回。 */
    @Query("UPDATE achievement_progress SET current = :value, updatedAt = :now WHERE defId = :defId")
    suspend fun setCurrent(defId: String, value: Long, now: Long)
}
