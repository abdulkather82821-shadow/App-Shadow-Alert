package com.shadowalert.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shadowalert.app.domain.model.AppCategory

@Entity(
    tableName = "app_limits",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class AppLimitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val dailyLimitMinutes: Int,
    val currentUsageMinutes: Int = 0,
    val enabled: Boolean = true,
    val dailyLimitNotifiedEpochDay: Long = -1L,
    val dailyExtraMinutes: Int = 0,
    val overrideUntilMs: Long = 0L
) {
    val effectiveDailyLimitMinutes: Int
        get() = (dailyLimitMinutes + dailyExtraMinutes).coerceAtLeast(1)
}
