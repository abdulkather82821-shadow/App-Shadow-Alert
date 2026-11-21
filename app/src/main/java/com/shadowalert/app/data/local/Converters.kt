package com.shadowalert.app.data.local

import androidx.room.TypeConverter
import com.shadowalert.app.domain.model.AlertType
import com.shadowalert.app.domain.model.AlertUserResponse
import com.shadowalert.app.domain.model.AppCategory
import com.shadowalert.app.domain.model.SessionCompletionStatus

class Converters {
    @TypeConverter
    fun fromAppCategory(value: AppCategory): String = value.name

    @TypeConverter
    fun toAppCategory(value: String): AppCategory = AppCategory.fromString(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionCompletionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionCompletionStatus =
        SessionCompletionStatus.entries.firstOrNull { it.name == value }
            ?: SessionCompletionStatus.INTERRUPTED

    @TypeConverter
    fun fromAlertType(value: AlertType): String = value.name

    @TypeConverter
    fun toAlertType(value: String): AlertType =
        AlertType.entries.firstOrNull { it.name == value } ?: AlertType.INTENDED_DURATION_REACHED

    @TypeConverter
    fun fromAlertUserResponse(value: AlertUserResponse): String = value.name

    @TypeConverter
    fun toAlertUserResponse(value: String): AlertUserResponse =
        AlertUserResponse.entries.firstOrNull { it.name == value } ?: AlertUserResponse.NONE
}
