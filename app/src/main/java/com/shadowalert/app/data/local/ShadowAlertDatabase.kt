package com.shadowalert.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shadowalert.app.data.local.dao.AlertEventDao
import com.shadowalert.app.data.local.dao.AppLimitDao
import com.shadowalert.app.data.local.dao.DailyAppUsageDao
import com.shadowalert.app.data.local.dao.IntentSessionDao
import com.shadowalert.app.data.local.dao.ReflectionDao
import com.shadowalert.app.data.local.entity.AlertEventEntity
import com.shadowalert.app.data.local.entity.AppLimitEntity
import com.shadowalert.app.data.local.entity.DailyAppUsageEntity
import com.shadowalert.app.data.local.entity.IntentSessionEntity
import com.shadowalert.app.data.local.entity.ReflectionEntity

@Database(
    entities = [
        AppLimitEntity::class,
        IntentSessionEntity::class,
        AlertEventEntity::class,
        ReflectionEntity::class,
        DailyAppUsageEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ShadowAlertDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao
    abstract fun intentSessionDao(): IntentSessionDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun reflectionDao(): ReflectionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowAlertDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN dailyExtraMinutes INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN overrideUntilMs INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE alert_events ADD COLUMN packageName TEXT"
                )
                db.execSQL(
                    "ALTER TABLE alert_events ADD COLUMN details TEXT"
                )
            }
        }

        fun getInstance(context: Context): ShadowAlertDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShadowAlertDatabase::class.java,
                    "shadow_alert.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
