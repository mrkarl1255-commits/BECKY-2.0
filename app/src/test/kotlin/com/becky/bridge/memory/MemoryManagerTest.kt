package com.becky.bridge.memory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit tests for [MemoryManager] (Fase 3.2, Bloque 2).
 *
 * Uses an in-memory [InMemoryMemoryRepository] fake instead of
 * [RoomMemoryRepository], so these tests need no Android runtime,
 * emulator or Robolectric - only plain JUnit4 + `kotlinx.coroutines.runBlocking`
 * (both already available, no new Gradle test dependency added).
 */
class MemoryManagerTest {

    /** Simple in-memory [MemoryRepository] fake for tests: no Room, no Context. */
    private class InMemoryMemoryRepository : MemoryRepository {
        private val state = MutableStateFlow<Map<String, MemoryEntry>>(emptyMap())

        override suspend fun create(entry: MemoryEntry) {
            state.value = state.value + (entry.id to entry)
        }

        override suspend fun update(entry: MemoryEntry) {
            if (state.value.containsKey(entry.id)) {
                state.value = state.value + (entry.id to entry)
            }
        }

        override suspend fun read(id: String): MemoryEntry? = state.value[id]

        override suspend fun softDelete(id: String) {
            state.value[id]?.let { state.value = state.value + (id to it.copy(isDeleted = true)) }
        }

        override suspend fun restore(id: String) {
            state.value[id]?.let { state.value = state.value + (id to it.copy(isDeleted = false)) }
        }

        override suspend fun hardDelete(id: String) {
            state.value = state.value - id
        }

        override suspend fun touchLastAccessed(id: String, accessedAt: Long) {
            state.value[id]?.let { state.value = state.value + (id to it.copy(lastAccessedAt = accessedAt)) }
        }

        override suspend fun getAllActive(): List<MemoryEntry> =
            state.value.values.filterNot { it.isDeleted }.sortedByDescending { it.timestamp }

        override suspend fun getActiveByType(type: MemoryType): List<MemoryEntry> =
            state.value.values.filter { !it.isDeleted && it.type == type }.sortedByDescending { it.timestamp }

        override fun observeActive(): Flow<List<MemoryEntry>> =
            state.map { it.values.filterNot { e -> e.isDeleted }.sortedByDescending { e -> e.timestamp } }

        override suspend fun exportAll(): List<MemoryEntry> =
            state.value.values.sortedByDescending { it.timestamp }
    }

    private fun workingItem(
        id: String = "wm-1",
        origin: MemoryOrigin = MemoryOrigin.USER_STATED,
        confidence: Float = 0.9f,
        importance: Float = 0.5f,
        content: String = "user likes jazz music"
    ) = WorkingMemoryItem(
        id = id,
        content = content,
        timestamp = 5_000L,
        importance = importance,
        origin = origin,
        confidence = confidence,
        lastAccessedAt = 5_000L,
        updatable = true
    )

    // ---- Working Memory stays RAM-only ----

    @Test
    fun `remember stores only in RAM and never touches the repository`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 10_000L })

        manager.remember(workingItem())

        assertEquals(1, manager.allWorking().size)
        assertTrue("working memory must never be auto-persisted", repo.getAllActive().isEmpty())
    }

    @Test
    fun `discardWorking drops an item without ever persisting it`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 10_000L })
        manager.remember(workingItem(id = "wm-x"))

        manager.discardWorking("wm-x")

        assertNull(manager.recallWorking("wm-x"))
        assertTrue(repo.getAllActive().isEmpty())
    }

    // ---- Promotion goes through admission ----

    @Test
    fun `promote rejected by admission policy leaves the working item untouched and persists nothing`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(
            repo,
            admissionPolicy = MemoryAdmissionPolicy(),
            clock = { 10_000L },
            idGenerator = { "generated-id" }
        )
        // INFERRED origin, low importance, no repeats -> no trigger satisfied.
        manager.remember(workingItem(id = "wm-2", origin = MemoryOrigin.INFERRED, importance = 0.1f))

        val result = manager.promote("wm-2", MemoryType.SEMANTIC, existingSimilarCount = 0)

        assertFalse(result.decision.admitted)
        assertNull(result.entry)
        assertNotNull("rejected candidate must remain in working memory", manager.recallWorking("wm-2"))
        assertTrue(repo.getAllActive().isEmpty())
    }

    @Test
    fun `promote admitted by explicit statement persists a MemoryEntry and evicts the working item`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(
            repo,
            clock = { 20_000L },
            idGenerator = { "generated-id-1" }
        )
        manager.remember(workingItem(id = "wm-3", origin = MemoryOrigin.USER_STATED, confidence = 0.7f))

        val result = manager.promote("wm-3", MemoryType.USER, existingSimilarCount = 0)

        assertTrue(result.decision.admitted)
        assertEquals(AdmissionReason.EXPLICIT_STATEMENT, result.decision.reason)
        val entry = requireNotNull(result.entry)
        assertEquals("generated-id-1", entry.id)
        assertEquals(MemoryType.USER, entry.type)
        assertEquals("user likes jazz music", entry.content)
        assertEquals(20_000L, entry.lastAccessedAt)
        assertNull("promoted item must be evicted from working memory", manager.recallWorking("wm-3"))

        val persisted = repo.read("generated-id-1")
        assertNotNull(persisted)
        assertEquals(MemoryType.USER, persisted!!.type)
        assertFalse(persisted.isDeleted)
    }

    @Test
    fun `promote with an unknown candidateId is rejected without touching the repository`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 20_000L })

        val result = manager.promote("does-not-exist", MemoryType.EPISODIC)

        assertFalse(result.decision.admitted)
        assertNull(result.entry)
        assertTrue(repo.getAllActive().isEmpty())
    }

    // ---- Persistent CRUD via MemoryManager ----

    @Test
    fun `update on an updatable entry changes fields and refreshes lastAccessedAt`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 30_000L })
        repo.create(
            MemoryEntry(
                id = "m-1", type = MemoryType.SEMANTIC, content = "old content",
                timestamp = 1_000L, importance = 0.4f, origin = MemoryOrigin.INFERRED,
                confidence = 0.6f, lastAccessedAt = 1_000L, updatable = true
            )
        )

        val updated = manager.update("m-1", content = "new content", importance = 0.9f)

        assertNotNull(updated)
        assertEquals("new content", updated!!.content)
        assertEquals(0.9f, updated.importance)
        assertEquals(30_000L, updated.lastAccessedAt)
    }

    @Test
    fun `update on a non-updatable entry is rejected and persists nothing`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 30_000L })
        repo.create(
            MemoryEntry(
                id = "m-2", type = MemoryType.EPISODIC, content = "first ever connection to the watch",
                timestamp = 1_000L, importance = 1.0f, origin = MemoryOrigin.SYSTEM,
                confidence = 1.0f, lastAccessedAt = 1_000L, updatable = false
            )
        )

        val result = manager.update("m-2", content = "tampered content")

        assertNull("non-updatable entries must reject update()", result)
        assertEquals("first ever connection to the watch", repo.read("m-2")!!.content)
    }

    @Test
    fun `forget then restore is fully reversible and never hard-deletes`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 40_000L })
        repo.create(
            MemoryEntry(
                id = "m-3", type = MemoryType.CONVERSATIONAL, content = "some takeaway",
                timestamp = 1_000L, importance = 0.5f, origin = MemoryOrigin.INFERRED,
                confidence = 0.7f, lastAccessedAt = 1_000L, updatable = true
            )
        )

        manager.forget("m-3")
        assertTrue(manager.getAllActive().none { it.id == "m-3" })
        assertNotNull("soft-deleted entry must still be readable directly", manager.read("m-3"))
        assertTrue(manager.read("m-3")!!.isDeleted)

        manager.restore("m-3")
        assertTrue(manager.getAllActive().any { it.id == "m-3" })
        assertFalse(manager.read("m-3")!!.isDeleted)
    }

    @Test
    fun `exportAll includes soft-deleted entries, getAllActive does not`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 50_000L })
        repo.create(
            MemoryEntry(
                id = "m-4", type = MemoryType.USER, content = "prefers dark mode",
                timestamp = 1_000L, importance = 0.3f, origin = MemoryOrigin.USER_STATED,
                confidence = 0.9f, lastAccessedAt = 1_000L, updatable = true
            )
        )
        manager.forget("m-4")

        assertTrue(manager.getAllActive().isEmpty())
        val exported = manager.exportAll()
        assertEquals(1, exported.size)
        assertTrue(exported.first().isDeleted)
    }

    @Test
    fun `getActiveByType only returns entries of the requested persistent type`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val manager = MemoryManager(repo, clock = { 60_000L })
        repo.create(
            MemoryEntry(
                id = "m-5", type = MemoryType.SEMANTIC, content = "fact",
                timestamp = 1_000L, importance = 0.2f, origin = MemoryOrigin.SYSTEM,
                confidence = 0.8f, lastAccessedAt = 1_000L, updatable = true
            )
        )
        repo.create(
            MemoryEntry(
                id = "m-6", type = MemoryType.EPISODIC, content = "event",
                timestamp = 1_000L, importance = 0.2f, origin = MemoryOrigin.SYSTEM,
                confidence = 0.8f, lastAccessedAt = 1_000L, updatable = true
            )
        )

        val semanticOnly = manager.getActiveByType(MemoryType.SEMANTIC)

        assertEquals(1, semanticOnly.size)
        assertEquals("m-5", semanticOnly.first().id)
    }
}
