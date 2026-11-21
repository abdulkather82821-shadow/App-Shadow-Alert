package com.shadowalert.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.AlertUserResponse

@Entity(
    tableName = "alert_events",
    indices = [Index(value = ["sessionId"])]
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val alertType: AlertType,
    val timestamp: Long,
    val userResponse: AlertUserResponse = AlertUserResponse.NONE,
    val packageName: String? = null,
    val details: String? = null
)
