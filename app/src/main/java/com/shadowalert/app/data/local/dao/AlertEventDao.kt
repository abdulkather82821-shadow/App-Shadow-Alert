package com.shadowalert.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shadowalert.app.data.local.entity.AlertEventEntity
import com.shadowalert.app.domain.model.AlertUserResponse
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getForSession(sessionId: Long): List<AlertEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AlertEventEntity): Long

    @Query("UPDATE alert_events SET userResponse = :response, details = COALESCE(:details, details) WHERE id = :id")
    suspend fun updateResponse(id: Long, response: AlertUserResponse, details: String? = null)

    @Query(
        "UPDATE alert_events SET userResponse = :response, details = COALESCE(:details, details) " +
            "WHERE sessionId = :sessionId AND userResponse = 'NONE'"
    )
    suspend fun updateLatestUnansweredForSession(
        sessionId: Long,
        response: AlertUserResponse,
        details: String? = null
    )

    @Query("DELETE FROM alert_events")
    suspend fun deleteAll()
}
