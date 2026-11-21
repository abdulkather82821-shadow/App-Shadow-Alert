package com.shadowalert.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAppUsageDao {
    @Query("SELECT * FROM daily_app_usage WHERE epochDay >= :fromEpochDay ORDER BY epochDay ASC")
    fun observeSince(fromEpochDay: Long): Flow<List<DailyAppUsageEntity>>

    @Query(
        "SELECT * FROM daily_app_usage WHERE packageName = :packageName AND epochDay >= :fromEpochDay ORDER BY epochDay ASC"
    )
    fun observeForPackageSince(packageName: String, fromEpochDay: Long): Flow<List<DailyAppUsageEntity>>

    @Query(
        "SELECT * FROM daily_app_usage WHERE packageName = :packageName AND epochDay = :epochDay LIMIT 1"
    )
    suspend fun getByPackageAndDay(packageName: String, epochDay: Long): DailyAppUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DailyAppUsageEntity)

    @Query("DELETE FROM daily_app_usage")
    suspend fun deleteAll()
}
