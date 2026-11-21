package com.shadowalert.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shadowalert.app.domain.model.SessionCompletionStatus

@Entity(
    tableName = "intent_sessions",
    indices = [Index(value = ["appId"]), Index(value = ["completionStatus"])]
)
data class IntentSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val appId: Long,
    val packageName: String,
    val appName: String,
    val intendedDuration: Int,
    val startTime: Long,
    val endTime: Long? = null,
    val actualDuration: Int = 0,
    val accumulatedActiveMs: Long = 0L,
    val completionStatus: SessionCompletionStatus = SessionCompletionStatus.ACTIVE,
    val optionalPurpose: String? = null
)
