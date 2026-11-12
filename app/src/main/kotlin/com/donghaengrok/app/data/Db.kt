package com.donghaengrok.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 여정은 요약(JourneySummary)을 JSON으로 통째로 저장하고, 목록·필터에 쓰는 값만 컬럼으로 뺀다.
 * MVP 규모(여정 수백 개)에서는 이 방식이 가장 단순하다.
 */
@Entity(tableName = "journey")
data class JourneyEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val final: Boolean,
    val category: String?,
    val categorySource: String?,
    val title: String,
    val distanceKm: Double,
    val summaryJson: String,
)

@Entity(tableName = "unlock")
data class UnlockEntity(
    @PrimaryKey val ruleId: String,
    val unlockedAt: Long,
    val journeyId: String,
    val seen: Boolean = false,
)

@Entity(tableName = "anchor")
data class AnchorEntity(
    @PrimaryKey val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val source: String,
)

@Entity(tableName = "checkpoint")
data class CheckpointEntity(@PrimaryKey val id: Int = 0, val json: String, val savedAt: Long)

@Dao
interface JourneyDao {
    @Upsert suspend fun upsert(e: JourneyEntity)
    @Query("SELECT * FROM journey WHERE id = :id") suspend fun get(id: String): JourneyEntity?
    @Query("SELECT * FROM journey ORDER BY startedAt DESC LIMIT 1") suspend fun latest(): JourneyEntity?
    @Query("SELECT * FROM journey ORDER BY startedAt DESC") fun observeAll(): Flow<List<JourneyEntity>>
    @Query("SELECT * FROM journey WHERE category IS NOT NULL ORDER BY startedAt") suspend fun confirmed(): List<JourneyEntity>
    @Query("DELETE FROM journey WHERE id = :id") suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertUnlocks(e: List<UnlockEntity>): List<Long>
    @Query("SELECT * FROM unlock ORDER BY unlockedAt DESC") fun observeUnlocks(): Flow<List<UnlockEntity>>
    @Query("SELECT ruleId FROM unlock") suspend fun unlockedIds(): List<String>

    @Upsert suspend fun upsertAnchor(e: AnchorEntity)
    @Query("SELECT * FROM anchor") suspend fun anchors(): List<AnchorEntity>

    @Upsert suspend fun saveCheckpoint(e: CheckpointEntity)
    @Query("SELECT * FROM checkpoint WHERE id = 0") suspend fun checkpoint(): CheckpointEntity?
    @Query("DELETE FROM checkpoint") suspend fun clearCheckpoint()
}

@Database(entities = [JourneyEntity::class, UnlockEntity::class, AnchorEntity::class, CheckpointEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journeys(): JourneyDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "donghaengrok.db").build()
    }
}
