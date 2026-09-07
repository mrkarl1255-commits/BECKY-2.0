package com.becky.bridge.identity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit tests for [IdentityManager] (Fase 3.2, Bloque 1).
 *
 * Deliberately uses an in-memory [InMemoryIdentityRepository] fake instead
 * of [DataStoreIdentityRepository] so these tests need no Android runtime,
 * emulator or Robolectric - only plain JUnit4 (already a project
 * dependency) and `kotlinx.coroutines.runBlocking` (already transitively
 * available via `kotlinx-coroutines-core`, an existing `implementation`
 * dependency). No new Gradle test dependency was added for this file.
 */
class IdentityManagerTest {

    /** Simple in-memory [IdentityRepository] fake for tests: no DataStore, no Context. */
    private class InMemoryIdentityRepository : IdentityRepository {
        private val state = MutableStateFlow<BeckyIdentity?>(null)

        override suspend fun load(): BeckyIdentity? = state.value

        override suspend fun save(identity: BeckyIdentity) {
            state.value = identity
        }

        override fun observe(): Flow<BeckyIdentity?> = state
    }

    @Test
    fun `ensureInitialized creates and persists a default identity on first call`() = runBlocking {
        val repo = InMemoryIdentityRepository()
        assertNull("repository should start empty", repo.load())

        val manager = IdentityManager(repo, clock = { 1_000L })
        val identity = manager.ensureInitialized()

        assertEquals("BECKY", identity.name)
        assertEquals("Rebecca", identity.displayName)
        assertEquals(1_000L, identity.createdAt)
        assertEquals(1, identity.identityVersion)
        assertEquals(1, identity.personalityVersion)
        assertEquals(BeckyIdentity.DEFAULT_TRAITS, identity.traits)
        assertTrue("evolutionLog should start empty", identity.evolutionLog.isEmpty())

        // Second call must NOT overwrite the already-persisted identity.
        val second = manager.ensureInitialized()
        assertEquals(identity, second)
        assertEquals(identity, repo.load())
    }

    @Test
    fun `evolvePersonality below default threshold is rejected and persists nothing`() = runBlocking {
        val repo = InMemoryIdentityRepository()
        val manager = IdentityManager(repo, clock = { 2_000L })
        manager.ensureInitialized()

        val result = manager.evolvePersonality(
            description = "low confidence signal, should be ignored",
            origin = IdentityFieldOrigin.INFERRED,
            confidence = IdentityManager.DEFAULT_MIN_CONFIDENCE_TO_APPLY - 0.1f,
            addedTraits = listOf(PersonalityTrait.HUMOROUS)
        )

        assertNull("low-confidence evolution must be rejected", result)
        val persisted = requireNotNull(repo.load())
        assertEquals(1, persisted.personalityVersion)
        assertTrue(persisted.evolutionLog.isEmpty())
        assertFalse(persisted.traits.contains(PersonalityTrait.HUMOROUS))
    }

    @Test
    fun `minConfidenceToApply is configurable per instance, independent of the default`() = runBlocking {
        val repo = InMemoryIdentityRepository()
        // A stricter-than-default manager: 0.95 threshold instead of the 0.6 default.
        val strictManager = IdentityManager(repo, clock = { 2_500L }, minConfidenceToApply = 0.95f)
        strictManager.ensureInitialized()

        // 0.8 clears the class-wide DEFAULT_MIN_CONFIDENCE_TO_APPLY (0.6) but must
        // still be rejected by this instance's own, stricter 0.95 threshold.
        val rejectedByStrictInstance = strictManager.evolvePersonality(
            description = "confidence clears the default but not this instance's custom threshold",
            origin = IdentityFieldOrigin.INFERRED,
            confidence = 0.8f,
            addedTraits = listOf(PersonalityTrait.CURIOUS)
        )
        assertNull("0.8 must be rejected when minConfidenceToApply=0.95", rejectedByStrictInstance)

        // A lenient manager sharing the same repository: 0.3 threshold, well below default.
        val lenientManager = IdentityManager(repo, clock = { 2_600L }, minConfidenceToApply = 0.3f)
        val acceptedByLenientInstance = lenientManager.evolvePersonality(
            description = "confidence below the default but above this instance's lenient threshold",
            origin = IdentityFieldOrigin.INFERRED,
            confidence = 0.4f,
            addedTraits = listOf(PersonalityTrait.CURIOUS)
        )
        assertNotNull("0.4 must be accepted when minConfidenceToApply=0.3", acceptedByLenientInstance)
        checkNotNull(acceptedByLenientInstance)
        assertTrue(acceptedByLenientInstance.traits.contains(PersonalityTrait.CURIOUS))

        // Sanity: the class-wide default constant itself is unchanged (0.6f).
        assertEquals(0.6f, IdentityManager.DEFAULT_MIN_CONFIDENCE_TO_APPLY)
    }

    @Test
    fun `evolvePersonality at or above threshold is accepted, versioned and reversible`() = runBlocking {
        val repo = InMemoryIdentityRepository()
        val manager = IdentityManager(repo, clock = { 3_000L })
        val initial = manager.ensureInitialized()

        val evolved = manager.evolvePersonality(
            description = "user repeatedly enjoyed lighthearted jokes",
            origin = IdentityFieldOrigin.USER_STATED,
            confidence = 0.9f,
            addedTraits = listOf(PersonalityTrait.HUMOROUS),
            addedPreferences = mapOf("favoriteTopic" to "musica")
        )

        assertNotNull(evolved)
        checkNotNull(evolved)
        assertEquals(initial.personalityVersion + 1, evolved.personalityVersion)
        assertTrue(evolved.traits.contains(PersonalityTrait.HUMOROUS))
        assertEquals("musica", evolved.preferences["favoriteTopic"])
        assertEquals(1, evolved.evolutionLog.size)

        val record = evolved.evolutionLog.first()
        assertEquals(initial.personalityVersion, record.fromVersion)
        assertEquals(evolved.personalityVersion, record.toVersion)
        assertEquals(listOf(PersonalityTrait.HUMOROUS), record.addedTraits)
        assertEquals(listOf("favoriteTopic"), record.addedPreferenceKeys)

        // Now revert: must undo exactly this change, nothing more.
        val reverted = manager.revertLastEvolution()
        assertEquals(initial.personalityVersion, reverted.personalityVersion)
        assertFalse(reverted.traits.contains(PersonalityTrait.HUMOROUS))
        assertFalse(reverted.preferences.containsKey("favoriteTopic"))
        assertTrue(reverted.evolutionLog.isEmpty())
    }

    @Test
    fun `revertLastEvolution on an identity with no history is a no-op`() = runBlocking {
        val repo = InMemoryIdentityRepository()
        val manager = IdentityManager(repo, clock = { 4_000L })
        val initial = manager.ensureInitialized()

        val reverted = manager.revertLastEvolution()

        assertEquals(initial, reverted)
    }

    @Test
    fun `duplicate traits added by evolvePersonality are not duplicated`() = runBlocking {
        val repo = InMemoryIdentityRepository()
        val manager = IdentityManager(repo, clock = { 5_000L })
        manager.ensureInitialized()

        // WARM is already a default trait - adding it again must not duplicate it.
        val evolved = manager.evolvePersonality(
            description = "reinforcing an existing trait",
            origin = IdentityFieldOrigin.SYSTEM,
            confidence = 1.0f,
            addedTraits = listOf(PersonalityTrait.WARM)
        )

        assertNotNull(evolved)
        checkNotNull(evolved)
        assertEquals(1, evolved.traits.count { it == PersonalityTrait.WARM })
    }
}
