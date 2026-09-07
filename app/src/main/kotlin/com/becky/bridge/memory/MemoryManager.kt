package com.becky.bridge.memory

import android.content.Context
import java.util.UUID

/**
 * Controlled access point to BECKY's Memory (Fase 3.2, Bloque 2).
 *
 * Coordinates [WorkingMemoryStore] (RAM), [MemoryAdmissionPolicy] (the
 * promotion gate) and [MemoryRepository] (Room-backed persistence), but
 * contains no persistence or storage details itself - matching the
 * project spec's "mantener separacion clara entre modelo, repositorio y
 * logica de gestion" (model = [MemoryModels], repository =
 * [MemoryRepository], management/business-rule logic = this class +
 * [MemoryAdmissionPolicy]).
 *
 * This is the ONLY place a [WorkingMemoryItem] can ever become a
 * persisted [MemoryEntry]: [remember] adds to RAM only, and [promote]
 * is the sole, explicit, admission-gated bridge into [MemoryRepository].
 * Nothing calls [promote] automatically - callers (e.g. a future
 * Cognitive Core block) decide when a working-memory item is a promotion
 * candidate.
 *
 * Has NO dependency on [com.becky.bridge.bluetooth.BridgeRepository],
 * [com.becky.bridge.bluetooth.GattManager] or any BLE type, and NO
 * dependency on [com.becky.bridge.identity.IdentityManager] - Memory
 * stays decoupled from both the watch connection and Identity, per the
 * project spec's additive-compatibility / no-unnecessary-coupling rule.
 *
 * @param repository where promoted [MemoryEntry]s are persisted. Injected
 *   (rather than hardcoded to [RoomMemoryRepository]) so plain JVM unit
 *   tests can supply an in-memory fake with no Android/Room dependency.
 * @param workingMemory the RAM-only store backing [remember]/[recallWorking].
 *   Injected so tests can supply a fresh, isolated instance per test.
 * @param admissionPolicy the promotion gate used by [promote]. Injected so
 *   its thresholds can be tuned without touching this class (see
 *   [MemoryAdmissionPolicy]'s own configurability doc).
 * @param clock supplies "now" in epoch millis; injected for deterministic
 *   unit tests. Defaults to the real system clock.
 * @param idGenerator supplies a fresh unique id for newly promoted entries;
 *   injected for deterministic unit tests. Defaults to [UUID.randomUUID].
 */
class MemoryManager(
    private val repository: MemoryRepository,
    private val workingMemory: WorkingMemoryStore = WorkingMemoryStore(),
    private val admissionPolicy: MemoryAdmissionPolicy = MemoryAdmissionPolicy(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {

    // ---- Working Memory (RAM only - never touches repository on its own) ----

    /** Adds/replaces [item] in RAM-only Working Memory. Never persisted automatically. */
    fun remember(item: WorkingMemoryItem) {
        workingMemory.put(item)
    }

    /** Reads a single Working Memory item by id, or null if absent. */
    fun recallWorking(id: String): WorkingMemoryItem? = workingMemory.get(id)

    /** All current Working Memory items (RAM only). */
    fun allWorking(): List<WorkingMemoryItem> = workingMemory.all()

    /** Drops a Working Memory item without ever persisting it. */
    fun discardWorking(id: String) {
        workingMemory.evict(id)
    }

    // ---- Promotion: the ONLY path from Working Memory into persistence ----

    /**
     * Evaluates [candidateId] (an existing [WorkingMemoryItem]) against
     * [admissionPolicy] and, if admitted, creates a persistent [MemoryEntry]
     * of [targetType] for it via [repository]. The source working-memory
     * item is evicted from RAM on successful promotion (it now lives on as
     * the persisted entry instead) but left untouched if admission is
     * rejected.
     *
     * @param candidateId id of an item currently in [WorkingMemoryStore].
     * @param targetType which persistent [MemoryType] to promote into
     *   (never [MemoryType.WORKING] - enforced by the type system itself,
     *   since [MemoryType.WORKING] is a valid enum value but callers are
     *   expected to never pass it; [MemoryRepository] has no method that
     *   would accept it anyway).
     * @param existingSimilarCount forwarded to [MemoryAdmissionPolicy.evaluate] -
     *   see that function's doc.
     * @return the [AdmissionDecision] plus the newly created [MemoryEntry]
     *   when admitted (`entry` is null when rejected, or when [candidateId]
     *   is not currently in Working Memory).
     */
    suspend fun promote(
        candidateId: String,
        targetType: MemoryType,
        existingSimilarCount: Int = 0
    ): PromotionResult {
        val candidate = workingMemory.get(candidateId)
            ?: return PromotionResult(
                decision = AdmissionDecision(
                    admitted = false,
                    reason = AdmissionReason.SIGNIFICANT_EVENT,
                    explanation = "no Working Memory item found for id=$candidateId"
                ),
                entry = null
            )

        val decision = admissionPolicy.evaluate(candidate, existingSimilarCount)
        if (!decision.admitted) {
            return PromotionResult(decision = decision, entry = null)
        }

        val entry = MemoryEntry(
            id = idGenerator(),
            type = targetType,
            content = candidate.content,
            timestamp = candidate.timestamp,
            importance = candidate.importance,
            origin = candidate.origin,
            confidence = candidate.confidence,
            lastAccessedAt = clock(),
            updatable = candidate.updatable
        )
        repository.create(entry)
        workingMemory.evict(candidateId)
        return PromotionResult(decision = decision, entry = entry)
    }

    // ---- Persistent Memory CRUD (delegates to repository; adds update-rules) ----

    /** Reads a single persistent entry by id (soft-deleted or not). */
    suspend fun read(id: String): MemoryEntry? = repository.read(id)

    /**
     * Updates [id]'s [content]/[importance]/[confidence] and refreshes
     * [MemoryEntry.lastAccessedAt] to now. Rejected (returns null, persists
     * nothing) when the entry does not exist or [MemoryEntry.updatable] is
     * false - protecting the historical accuracy of entries explicitly
     * marked non-updatable (e.g. a fixed "first connection" record).
     */
    suspend fun update(id: String, content: String? = null, importance: Float? = null, confidence: Float? = null): MemoryEntry? {
        val existing = repository.read(id) ?: return null
        if (!existing.updatable) return null

        val updated = existing.copy(
            content = content ?: existing.content,
            importance = importance ?: existing.importance,
            confidence = confidence ?: existing.confidence,
            lastAccessedAt = clock()
        )
        repository.update(updated)
        return updated
    }

    /** Soft-deletes ("forgets") a persistent entry - reversible via [restore]. */
    suspend fun forget(id: String) = repository.softDelete(id)

    /** Reverses a prior [forget]. */
    suspend fun restore(id: String) = repository.restore(id)

    /** Permanently removes a persistent entry. Reserved for a future explicit "erase forever" action. */
    suspend fun eraseForever(id: String) = repository.hardDelete(id)

    /** Marks [id] as accessed right now (updates [MemoryEntry.lastAccessedAt]); use whenever a memory is recalled. */
    suspend fun markAccessed(id: String) = repository.touchLastAccessed(id, clock())

    /** All active persistent entries, most recent first. */
    suspend fun getAllActive(): List<MemoryEntry> = repository.getAllActive()

    /** All active persistent entries of a single [type], most recent first. */
    suspend fun getActiveByType(type: MemoryType): List<MemoryEntry> = repository.getActiveByType(type)

    /** Full export (active + soft-deleted) of persistent memory, most recent first. */
    suspend fun exportAll(): List<MemoryEntry> = repository.exportAll()

    companion object {
        @Volatile
        private var INSTANCE: MemoryManager? = null

        /**
         * App-wide singleton backed by [RoomMemoryRepository], matching the
         * [com.becky.bridge.bluetooth.BridgeRepository.getInstance] /
         * [com.becky.bridge.identity.IdentityManager.getInstance] pattern
         * already used elsewhere in the project. Not called from anywhere
         * yet in Bloque 2 (no existing file was modified to wire this in) -
         * reserved for a future integration block.
         */
        fun getInstance(context: Context): MemoryManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryManager(RoomMemoryRepository(context.applicationContext))
                    .also { INSTANCE = it }
            }
    }
}

/** Outcome of [MemoryManager.promote]: the [AdmissionDecision] plus the resulting [MemoryEntry] when admitted. */
data class PromotionResult(
    val decision: AdmissionDecision,
    val entry: MemoryEntry?
)
