package com.maodou.notify.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 成就分类，可由外部配置写入；sortOrder 控制展示顺序。 */
@Entity(tableName = "achievement_categories")
data class AchievementCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0
)

/**
 * 成就定义：配置与进度彻底分离。
 * conditionJson / progressJson / rewardJson 分别对应 achievement 包的三个 Spec，
 * 新增成就类型无需改表结构（只加新的 type 枚举与引擎分支）。
 */
@Entity(tableName = "achievement_defs", indices = [Index("categoryId")])
data class AchievementDefEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val conditionJson: String,
    val progressJson: String,
    val rewardJson: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true
)

/** 用户进度。status 只会从 LOCKED 单向变为 UNLOCKED。 */
@Entity(tableName = "achievement_progress")
data class AchievementProgressEntity(
    @PrimaryKey val defId: String,
    val current: Long = 0L,
    val target: Long = 1L,
    val status: Int = 0,
    val unlockedAt: Long? = null,
    val updatedAt: Long = 0L
)

/** 奖励领取记录。claimKey 唯一保证重复领取幂等。 */
@Entity(tableName = "achievement_reward_claims", indices = [Index(value = ["claimKey"], unique = true)])
data class AchievementRewardClaimEntity(
    @PrimaryKey val defId: String,
    val claimKey: String,
    val claimedAt: Long
)

/** 事件去重账本。同一 eventKey 只会被消费一次，抗重复点击/重复投递。 */
@Entity(tableName = "achievement_events")
data class AchievementEventEntity(
    @PrimaryKey val eventKey: String,
    val defId: String,
    val type: String,
    val processedAt: Long
)
