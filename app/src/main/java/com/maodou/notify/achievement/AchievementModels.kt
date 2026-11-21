package com.maodou.notify.achievement

import org.json.JSONObject

/** 成就状态。已解锁不可逆，避免进度回退导致「成就被收回去」。 */
object AchievementStatus {
    const val LOCKED = 0
    const val UNLOCKED = 1
}

/** 触发条件类型。新增类型只需在此枚举 + Repository 的 matches() 增加分支。 */
enum class ConditionType {
    ANY_NOTIFICATION,   // 任意通知落库
    FILTERED,           // 被规则或 AI 过滤
    CATEGORY,           // 指定类型通知（如验证码）
    PACKAGE,            // 指定应用通知
    LEARNED,            // 用户标注样本
    RULE_CREATED,       // 创建规则
    PRO_ACTIVATED,      // 激活 Pro
    APP_OPENED,         // 打开 App（按自然日去重）
    ACHIEVEMENT_UNLOCKED // 级联：解锁了其它成就
}

/** 条件参数，JSON 存库（零新增依赖，Android 内置 org.json）。 */
data class ConditionSpec(
    val type: ConditionType = ConditionType.ANY_NOTIFICATION,
    val result: String? = null,
    val category: String? = null,
    val packageName: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("type", type.name)
        result?.let { put("result", it) }
        category?.let { put("category", it) }
        packageName?.let { put("packageName", it) }
    }.toString()

    companion object {
        fun fromJson(raw: String): ConditionSpec = try {
            val o = JSONObject(raw)
            ConditionSpec(
                type = runCatching { ConditionType.valueOf(o.optString("type")) }
                    .getOrDefault(ConditionType.ANY_NOTIFICATION),
                result = o.optString("result", "").ifBlank { null },
                category = o.optString("category", "").ifBlank { null },
                packageName = o.optString("packageName", "").ifBlank { null }
            )
        } catch (e: Exception) {
            ConditionSpec()
        }
    }
}

/** 进度推进方式：一次性 / 事件累加 / 从数据源绝对值校准。 */
enum class ProgressMode { ONCE, INCREMENT, ABSOLUTE }

/** 绝对值校准的数据源；INCREMENT 模式无需设置。 */
enum class ProgressSource { NONE, NOTIFICATIONS, FILTERED, LEARNED, RULES, PRO, UNLOCKS }

/**
 * 进度规则。[monotonic]=true 时只升不降，抗重复触发与乱序事件；
 * 需要人为回退时走显式的 resetProgress()，不影响已解锁状态。
 */
data class ProgressSpec(
    val mode: ProgressMode = ProgressMode.ONCE,
    val source: ProgressSource = ProgressSource.NONE,
    val target: Long = 1L,
    val step: Long = 1L,
    val monotonic: Boolean = true
) {
    fun toJson(): String = JSONObject().apply {
        put("mode", mode.name)
        put("source", source.name)
        put("target", target)
        put("step", step)
        put("monotonic", monotonic)
    }.toString()

    companion object {
        fun fromJson(raw: String): ProgressSpec = try {
            val o = JSONObject(raw)
            ProgressSpec(
                mode = runCatching { ProgressMode.valueOf(o.optString("mode")) }
                    .getOrDefault(ProgressMode.ONCE),
                source = runCatching { ProgressSource.valueOf(o.optString("source", "NONE")) }
                    .getOrDefault(ProgressSource.NONE),
                target = o.optLong("target", 1L).coerceAtLeast(1L),
                step = o.optLong("step", 1L).coerceAtLeast(1L),
                monotonic = o.optBoolean("monotonic", true)
            )
        } catch (e: Exception) {
            ProgressSpec()
        }
    }
}

/** 可选奖励。NONE 表示成就仅作展示，不产生领取流程。 */
enum class RewardType { NONE, TITLE, THEME, PRO_DAYS }

data class RewardSpec(
    val type: RewardType = RewardType.NONE,
    val code: String = "",
    val label: String = "",
    val amount: Int = 0
) {
    fun toJson(): String = JSONObject().apply {
        put("type", type.name)
        put("code", code)
        put("label", label)
        put("amount", amount)
    }.toString()

    companion object {
        fun fromJson(raw: String): RewardSpec = try {
            val o = JSONObject(raw)
            RewardSpec(
                type = runCatching { RewardType.valueOf(o.optString("type", "NONE")) }
                    .getOrDefault(RewardType.NONE),
                code = o.optString("code", ""),
                label = o.optString("label", ""),
                amount = o.optInt("amount", 0)
            )
        } catch (e: Exception) {
            RewardSpec()
        }
    }
}

/** 事件类型。调用方需保证同一业务事件的 [key] 稳定，用于幂等去重。 */
enum class EventType {
    NOTIFICATION, LEARNED, RULE_CREATED, PRO_ACTIVATED, APP_OPENED, ACHIEVEMENT_UNLOCKED
}

data class AchievementEvent(
    val key: String,
    val type: EventType,
    val result: String? = null,
    val category: String? = null,
    val packageName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** 列表/详情/进度查询统一视图模型。 */
data class AchievementView(
    val defId: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val current: Long,
    val target: Long,
    val status: Int,
    val unlockedAt: Long?,
    val rewardClaimedAt: Long?,
    val reward: RewardSpec,
    val condition: ConditionSpec,
    val sortOrder: Int
) {
    val ratio: Float get() = if (target <= 0) 0f else (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val isUnlocked: Boolean get() = status == AchievementStatus.UNLOCKED
}

/** 进度查询快照（供外部接口/小部件读取，不暴露 Room 实体）。 */
data class ProgressSnapshot(
    val defId: String,
    val current: Long,
    val target: Long,
    val status: Int,
    val unlockedAt: Long?,
    val rewardClaimedAt: Long?,
    val percent: Int
)

enum class ApplyOutcome { DUPLICATE, NO_CHANGE, ADVANCED, UNLOCKED }

data class ApplyResult(val outcome: ApplyOutcome, val current: Long, val unlocked: Boolean)

enum class ClaimResult { CLAIMED, ALREADY_CLAIMED, LOCKED, NOT_APPLICABLE, NOT_FOUND, KEY_CONFLICT }
