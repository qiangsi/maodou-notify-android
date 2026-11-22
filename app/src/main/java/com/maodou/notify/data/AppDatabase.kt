package com.maodou.notify.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotificationEntity::class, FilterRule::class, AiFeedback::class,
        AchievementCategoryEntity::class, AchievementDefEntity::class,
        AchievementProgressEntity::class, AchievementRewardClaimEntity::class,
        AchievementEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun ruleDao(): RuleDao
    abstract fun aiFeedbackDao(): AiFeedbackDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1→v2：通知新增 sysKey（精确消除）、category（类型筛选） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN sysKey TEXT")
                db.execSQL("ALTER TABLE notifications ADD COLUMN category TEXT")
            }
        }

        /** v2→v3：成就系统（分类 / 定义 / 进度 / 奖励领取 / 事件去重账本） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_categories (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_defs (id TEXT PRIMARY KEY NOT NULL, categoryId TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, iconKey TEXT NOT NULL, conditionJson TEXT NOT NULL, progressJson TEXT NOT NULL, rewardJson TEXT NOT NULL, sortOrder INTEGER NOT NULL, enabled INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_achievement_defs_categoryId ON achievement_defs (categoryId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_progress (defId TEXT PRIMARY KEY NOT NULL, current INTEGER NOT NULL, target INTEGER NOT NULL, status INTEGER NOT NULL, unlockedAt INTEGER, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_reward_claims (defId TEXT PRIMARY KEY NOT NULL, claimKey TEXT NOT NULL, claimedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_achievement_reward_claims_claimKey ON achievement_reward_claims (claimKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_events (eventKey TEXT PRIMARY KEY NOT NULL, defId TEXT NOT NULL, type TEXT NOT NULL, processedAt INTEGER NOT NULL)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maodou.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // P1 性能：补高频查询索引。用 onOpen + IF NOT EXISTS，不改 @Entity 注解，
                    // 因此不触发 Room schema 版本校验，已安装用户下次打开即自动建索引。
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_result ON notifications(result)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_category ON notifications(category)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_marked ON notifications(marked)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_ts_result ON notifications(timestamp, result)")
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
