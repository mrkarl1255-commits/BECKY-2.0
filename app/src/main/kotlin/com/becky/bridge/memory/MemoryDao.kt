package com.becky.bridge.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [MemoryEntity] (Fase 3.2, Bloque 2).
 *
 * Pure persistence access - no business rules (admission, relevance,
 * promotion) live here; those belong to [MemoryManager]/[MemoryAdmissionPolicy].
 * Soft-deleted rows ([MemoryEntity.isDeleted] = true) are excluded from the
 * "active" queries below but are never physically removed by this DAO
 * except via [hardDelete], which [MemoryRepository] never calls for normal
 * "forgetting" (only [MemoryRepository.softDelete]/[restore] are used for
 * that) - [hardDelete] exists solely to support a future, explicit
 * "permanently erase" user action, kept separate from soft-delete on
 * purpose.
 */
@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Update
    suspend fun update(entity: MemoryEntity)

    @Query("SELECT * FROM memory_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): MemoryEntity?

    @Query("SELECT * FROM memory_entries WHERE is_deleted = 0 ORDER BY timestamp DESC")
    fun observeActive(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entries WHERE is_deleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllActive(): List<MemoryEntity>

    @Query("SELECT * FROM memory_entries WHERE is_deleted = 0 AND type_name = :typeName ORDER BY timestamp DESC")
    suspend fun getActiveByType(typeName: String): List<MemoryEntity>

    @Query("SELECT * FROM memory_entries ORDER BY timestamp DESC")
    suspend fun getAllIncludingDeleted(): List<MemoryEntity>

    @Query("UPDATE memory_entries SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE memory_entries SET is_deleted = 0 WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE memory_entries SET last_accessed_at = :accessedAt WHERE id = :id")
    suspend fun touchLastAccessed(id: String, accessedAt: Long)

    /** Permanently removes a row. Reserved for a future explicit "erase forever" action - see class doc. */
    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun hardDelete(id: String)
}
