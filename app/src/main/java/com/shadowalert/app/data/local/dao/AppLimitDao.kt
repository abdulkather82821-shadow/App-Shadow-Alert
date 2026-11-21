package com.shadowalert.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.domain.model.AppCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits ORDER BY enabled DESC, appName ASC")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE enabled = 1 ORDER BY currentUsageMinutes DESC")
    fun observeEnabled(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE enabled = 1")
    suspend fun getEnabledList(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits")
    suspend fun getAllList(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppLimitEntity?

    @Query("SELECT * FROM app_limits WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AppLimitEntity?

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    fun observeByPackageName(packageName: String): Flow<AppLimitEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(appLimit: AppLimitEntity): Long

    @Update
    suspend fun update(appLimit: AppLimitEntity)

    @Query("UPDATE app_limits SET currentUsageMinutes = :minutes WHERE packageName = :packageName")
    suspend fun updateUsageMinutes(packageName: String, minutes: Int)

    @Query("UPDATE app_limits SET dailyLimitMinutes = :limitMinutes WHERE packageName = :packageName")
    suspend fun updateDailyLimit(packageName: String, limitMinutes: Int)

    @Query("UPDATE app_limits SET dailyExtraMinutes = dailyExtraMinutes + :extraMinutes WHERE packageName = :packageName")
    suspend fun addDailyExtraMinutes(packageName: String, extraMinutes: Int)

    @Query("UPDATE app_limits SET overrideUntilMs = :untilMs WHERE packageName = :packageName")
    suspend fun setOverrideUntil(packageName: String, untilMs: Long)

    @Query("UPDATE app_limits SET category = :category WHERE packageName = :packageName")
    suspend fun updateCategory(packageName: String, category: AppCategory)

    @Query("UPDATE app_limits SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE app_limits SET dailyLimitNotifiedEpochDay = :epochDay WHERE packageName = :packageName")
    suspend fun markDailyLimitNotified(packageName: String, epochDay: Long)

    @Query(
        "UPDATE app_limits SET currentUsageMinutes = 0, dailyLimitNotifiedEpochDay = -1, dailyExtraMinutes = 0, overrideUntilMs = 0"
    )
    suspend fun resetAllDailyUsage()

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM app_limits")
    suspend fun deleteAll()
}
