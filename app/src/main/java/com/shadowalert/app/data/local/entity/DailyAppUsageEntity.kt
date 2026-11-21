package com.shadowalert.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_app_usage",
    indices = [Index(value = ["packageName", "epochDay"], unique = true)]
)
data class DailyAppUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val epochDay: Long,
    val usageMinutes: Int
)
