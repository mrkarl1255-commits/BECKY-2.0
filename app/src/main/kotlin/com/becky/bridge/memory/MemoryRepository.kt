package com.becky.bridge.memory

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists [MemoryEntry] (Fase 3.2, Bloque 2). Covers full CRUD plus
 * soft-delete/restore ("olvido" reversible) and export, exactly as
 * required by the project spec. Contains NO business rules - admission,
 * relevance scoring and promotion from Working Memory all live in
 * [MemoryManager]/[MemoryAdmissionPolicy], never here.
 *
 * [MemoryType.WORKING] entries are never expected here: nothing in this
 * interface accepts or returns that type, since Working Memory lives only
 * in [WorkingMemoryStore] (RAM).
 */
interface MemoryRepository {
    /** Inserts a new entry, or fully replaces an existing one with the same [MemoryEntry.id]. */
    suspend fun create(entry: MemoryEntry)

    /** Updates an existing entry in place. No-op if [MemoryEntry.id] does not exist. */
    suspend fun update(entry: MemoryEntry)

    /** Reads a single entry by id, regardless of its soft-delete state. */
    suspend fun read(id: String): MemoryEntry?

    /** Soft-deletes ("forgets") an entry - reversible via [restore]. Never a hard row removal. */
    suspend fun softDelete(id: String)

    /** Reverses a prior [softDelete]. No-op if [id] was never soft-deleted. */
    suspend fun restore(id: String)

    /** Permanently removes a row. Reserved for a future explicit "erase forever" action - not used for normal forgetting. */
    suspend fun hardDelete(id: String)

    /** Updates [MemoryEntry.lastAccessedAt] to [accessedAt] for [id] (called whenever a memory is recalled). */
    suspend fun touchLastAccessed(id: String, accessedAt: Long)

    /** All active (non soft-deleted) entries, most recent first. */
    suspend fun getAllActive(): List<MemoryEntry>

    /** All active entries of a single persistent [MemoryType], most recent first. */
    suspend fun getActiveByType(type: MemoryType): List<MemoryEntry>

    /** Reactive stream of all active entries, most recent first. */
    fun observeActive(): Flow<List<MemoryEntry>>

    /**
     * All entries INCLUDING soft-deleted ones, most recent first - the full
     * export required by the project spec (e.g. for a future "export my
     * memory" user action). Corrupted rows that fail to map back to a
     * domain [MemoryEntry] are silently skipped (see [MemoryEntity.toDomainOrNull]).
     */
    suspend fun exportAll(): List<MemoryEntry>
}

/** Default [MemoryRepository] backed by Room ([MemoryDatabase]/[MemoryDao]). */
class RoomMemoryRepository(context: Context) : MemoryRepository {

    private val dao: MemoryDao = MemoryDatabase.getInstance(context.applicationContext).memoryDao()

    override suspend fun create(entry: MemoryEntry) {
        dao.insert(entry.toEntity())
    }

    override suspend fun update(entry: MemoryEntry) {
        dao.update(entry.toEntity())
    }

    override suspend fun read(id: String): MemoryEntry? =
        dao.findById(id)?.toDomainOrNull()

    override suspend fun softDelete(id: String) {
        dao.softDelete(id)
    }

    override suspend fun restore(id: String) {
        dao.restore(id)
    }

    override suspend fun hardDelete(id: String) {
        dao.hardDelete(id)
    }

    override suspend fun touchLastAccessed(id: String, accessedAt: Long) {
        dao.touchLastAccessed(id, accessedAt)
    }

    override suspend fun getAllActive(): List<MemoryEntry> =
        dao.getAllActive().mapNotNull { it.toDomainOrNull() }

    override suspend fun getActiveByType(type: MemoryType): List<MemoryEntry> =
        dao.getActiveByType(type.name).mapNotNull { it.toDomainOrNull() }

    override fun observeActive(): Flow<List<MemoryEntry>> =
        dao.observeActive().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun exportAll(): List<MemoryEntry> =
        dao.getAllIncludingDeleted().mapNotNull { it.toDomainOrNull() }
}
