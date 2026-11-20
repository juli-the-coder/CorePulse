package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GameSessionEntity::class, TelemetrySampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CorePulseDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: CorePulseDatabase? = null

        fun getInstance(context: Context): CorePulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CorePulseDatabase::class.java,
                    "corepulse_telemetry.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
