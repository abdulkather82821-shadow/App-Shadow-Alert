package com.shadowalert.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.domain.model.SessionCompletionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface IntentSessionDao {
    @Query("SELECT * FROM intent_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<IntentSessionEntity>>

    @Query("SELECT * FROM intent_sessions ORDER BY startTime DESC")
    suspend fun getAllList(): List<IntentSessionEntity>

    @Query("SELECT * FROM intent_sessions WHERE completionStatus = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    fun observeActiveSession(): Flow<IntentSessionEntity?>

    @Query("SELECT * FROM intent_sessions WHERE completionStatus = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): IntentSessionEntity?

    @Query("SELECT * FROM intent_sessions WHERE completionStatus = 'ACTIVE'")
    suspend fun getAllActiveSessions(): List<IntentSessionEntity>

    @Query("SELECT * FROM intent_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IntentSessionEntity?

    @Query("SELECT * FROM intent_sessions WHERE packageName = :packageName ORDER BY startTime DESC LIMIT 20")
    fun observeForPackage(packageName: String): Flow<List<IntentSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: IntentSessionEntity): Long

    @Update
    suspend fun update(session: IntentSessionEntity)

    @Query(
        "UPDATE intent_sessions SET actualDuration = :actualMinutes, accumulatedActiveMs = :accumulatedMs WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, actualMinutes: Int, accumulatedMs: Long)

    @Query(
        "UPDATE intent_sessions SET intendedDuration = :newIntendedMinutes, completionStatus = :status WHERE id = :id"
    )
    suspend fun extendSession(id: Long, newIntendedMinutes: Int, status: SessionCompletionStatus)

    @Query(
        "UPDATE intent_sessions SET endTime = :endTime, actualDuration = :actualMinutes, accumulatedActiveMs = :accumulatedMs, completionStatus = :status WHERE id = :id"
    )
    suspend fun finishSession(
        id: Long,
        endTime: Long,
        actualMinutes: Int,
        accumulatedMs: Long,
        status: SessionCompletionStatus
    )

    @Query("DELETE FROM intent_sessions")
    suspend fun deleteAll()
}
