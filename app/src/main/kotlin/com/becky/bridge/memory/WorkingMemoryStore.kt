package com.becky.bridge.memory

/**
 * BECKY's Working Memory: short-lived, RAM-only context (Fase 3.2, Bloque 2).
 *
 * Per the approved architecture, Working Memory is EXCLUSIVELY in-process
 * memory - this class has no Room/DataStore/file dependency whatsoever, and
 * nothing in it is ever written to disk automatically. The only path from
 * here into persistent storage is [MemoryManager.promote], which always
 * consults [MemoryAdmissionPolicy] first.
 *
 * Deliberately a plain, synchronized in-memory store (no Android
 * dependency) so it is trivially unit-testable on the JVM.
 */
class WorkingMemoryStore {

    private val lock = Any()
    private val items = LinkedHashMap<String, WorkingMemoryItem>()

    /** Adds or replaces [item] (by [WorkingMemoryItem.id]). */
    fun put(item: WorkingMemoryItem) {
        synchronized(lock) { items[item.id] = item }
    }

    /** Reads a single item, or null if absent (e.g. never added, or already evicted). */
    fun get(id: String): WorkingMemoryItem? =
        synchronized(lock) { items[id] }

    /** All items currently held, most recently added last. Never touches persistent storage. */
    fun all(): List<WorkingMemoryItem> =
        synchronized(lock) { items.values.toList() }

    /** Removes a single item (e.g. after it expires or is promoted). No-op if [id] is absent. */
    fun evict(id: String) {
        synchronized(lock) { items.remove(id) }
    }

    /** Clears all working memory (e.g. on a fresh conversational context). */
    fun clear() {
        synchronized(lock) { items.clear() }
    }

    /** Number of items currently held. */
    fun size(): Int =
        synchronized(lock) { items.size }
}
