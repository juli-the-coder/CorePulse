package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM game_sessions ORDER BY startTimeMillis DESC")
    fun observeAllSessions(): Flow<List<GameSessionEntity>>

    @Query("SELECT * FROM game_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<GameSessionEntity?>

    @Query("SELECT * FROM telemetry_samples WHERE sessionId = :sessionId ORDER BY elapsedSeconds ASC, id ASC")
    fun observeSamplesForSession(sessionId: Long): Flow<List<TelemetrySampleEntity>>

    @Query("SELECT * FROM telemetry_samples WHERE sessionId = :sessionId ORDER BY elapsedSeconds ASC, id ASC")
    suspend fun getSamplesForSessionOnce(sessionId: Long): List<TelemetrySampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: GameSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<TelemetrySampleEntity>)

    @Query("DELETE FROM game_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM game_sessions")
    suspend fun clearAllSessions()
}
