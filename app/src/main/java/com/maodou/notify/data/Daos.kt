package com.maodou.notify.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByPackage(pkg: String, limit: Int): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NotificationEntity): Long

    @Update
    suspend fun update(item: NotificationEntity)

    @Delete
    suspend fun delete(item: NotificationEntity)

    @Query("UPDATE notifications SET marked = :label WHERE id = :id")
    suspend fun setMarked(id: Long, label: Int)

    @Query("UPDATE notifications SET removedReason = :reason WHERE id = :id")
    suspend fun setRemovedReason(id: Long, reason: String)

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE result = :result")
    suspend fun countByResult(result: String): Int

    @Query("SELECT packageName, COUNT(*) AS cnt FROM notifications WHERE timestamp >= :since GROUP BY packageName ORDER BY cnt DESC")
    suspend fun packageRank(since: Long): List<PackageCount>

    @Query("SELECT timestamp, result FROM notifications WHERE timestamp >= :since")
    suspend fun dailyRaw(since: Long): List<DailyRaw>

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long)

    @Query("SELECT * FROM notifications WHERE result = :r AND marked IS NULL ORDER BY timestamp DESC LIMIT :limit")
    suspend fun unscored(r: String, limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NotificationEntity>
}

data class PackageCount(val packageName: String, val cnt: Int)
data class DailyRaw(val timestamp: Long, val result: String)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<FilterRule>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun enabledRules(): List<FilterRule>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): FilterRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: FilterRule): Long

    @Update
    suspend fun update(rule: FilterRule)

    @Delete
    suspend fun delete(rule: FilterRule)

    @Query("DELETE FROM rules")
    suspend fun clearAll()
}

@Dao
interface AiFeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: AiFeedback)

    @Query("SELECT COUNT(*) FROM ai_feedback WHERE label = 1")
    suspend fun spamCount(): Int

    @Query("SELECT COUNT(*) FROM ai_feedback WHERE label = 0")
    suspend fun normalCount(): Int

    @Query("SELECT * FROM ai_feedback ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AiFeedback>

    @Query("DELETE FROM ai_feedback")
    suspend fun clearAll()
}
