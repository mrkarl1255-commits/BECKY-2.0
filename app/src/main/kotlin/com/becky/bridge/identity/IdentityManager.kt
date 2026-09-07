package com.becky.bridge.identity

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

/**
 * Controlled access point to BECKY's [BeckyIdentity] (Fase 3.2, Bloque 1).
 *
 * This is the ONLY place identity is ever created or evolved:
 * - [ensureInitialized]/[currentIdentity] guarantee a persisted default
 *   identity exists from the very first run (see [BeckyIdentity.default]).
 * - [evolvePersonality] is the sole controlled, confidence-gated entry
 *   point for changing personality over time. Nothing calls it yet in
 *   Bloque 1 - it is reserved for a future Learning Engine block (per
 *   project spec section 9) - but it is included and unit-tested now so
 *   the reversibility contract already works before anything depends on
 *   it.
 * - [revertLastEvolution] undoes precisely the most recent accepted
 *   change, using the [PersonalityEvolutionRecord] it created.
 *
 * Deliberately has NO dependency on [com.becky.bridge.bluetooth.BridgeRepository],
 * [com.becky.bridge.bluetooth.GattManager] or any BLE type - identity is
 * decoupled from the watch connection, matching the additive-compatibility
 * rule in the project spec (section 5).
 *
 * @param repository where [BeckyIdentity] is actually persisted. Injected
 *   (rather than hardcoded to [DataStoreIdentityRepository]) so plain JVM
 *   unit tests can supply an in-memory fake with no Android/DataStore
 *   dependency.
 * @param clock supplies "now" in epoch millis; injected for deterministic
 *   unit tests. Defaults to the real system clock.
 * @param minConfidenceToApply configurable confidence threshold used by
 *   [evolvePersonality] (see [DEFAULT_MIN_CONFIDENCE_TO_APPLY]). Injected via
 *   the constructor - the same configuration mechanism already used for
 *   [repository]/[clock] in this layer - rather than hardcoded, so a future
 *   caller (e.g. the Learning Engine in a later Bloque) can tune how
 *   conservative personality evolution is without touching this class or
 *   coupling Identity to any external config store. Defaults to
 *   [DEFAULT_MIN_CONFIDENCE_TO_APPLY] (0.6f), preserving Bloque 1 behavior
 *   exactly when not overridden.
 */
class IdentityManager(
    private val repository: IdentityRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val minConfidenceToApply: Float = DEFAULT_MIN_CONFIDENCE_TO_APPLY
) {

    /**
     * Loads the persisted identity, creating and persisting a fresh
     * [BeckyIdentity.default] the very first time this is ever called
     * for this install. Always returns a non-null identity.
     */
    suspend fun ensureInitialized(): BeckyIdentity =
        repository.load() ?: BeckyIdentity.default(createdAt = clock()).also { repository.save(it) }

    /**
     * Reactive stream of BECKY's current identity. Guarantees a default
     * identity is persisted before the first value is collected (via
     * [ensureInitialized]), then mirrors [IdentityRepository.observe],
     * falling back to [ensureInitialized] again in the unlikely case an
     * observed value is null (e.g. underlying storage cleared at runtime).
     */
    fun currentIdentity(): Flow<BeckyIdentity> =
        repository.observe()
            .onStart { ensureInitialized() }
            .map { it ?: ensureInitialized() }

    /**
     * Controlled, reversible entry point for evolving BECKY's personality.
     *
     * Rejects the change (returns `null`, persists nothing) when
     * [confidence] is below [minConfidenceToApply] (configurable per
     * instance, defaults to [DEFAULT_MIN_CONFIDENCE_TO_APPLY]), honoring the
     * spec requirement that low-confidence signals must never become a
     * permanent trait. On acceptance, appends one [PersonalityEvolutionRecord]
     * to [BeckyIdentity.evolutionLog] describing exactly what was added,
     * so [revertLastEvolution] can undo precisely this change later.
     *
     * @param description human-readable reason for the change.
     * @param origin where this change came from (see [IdentityFieldOrigin]).
     * @param confidence 0.0-1.0 confidence that justifies accepting the change.
     * @param addedTraits new [PersonalityTrait]s to add (duplicates are ignored).
     * @param addedPreferences new/overwritten preference key-value pairs.
     * @return the updated, persisted [BeckyIdentity], or `null` if rejected.
     */
    suspend fun evolvePersonality(
        description: String,
        origin: IdentityFieldOrigin,
        confidence: Float,
        addedTraits: List<PersonalityTrait> = emptyList(),
        addedPreferences: Map<String, String> = emptyMap()
    ): BeckyIdentity? {
        if (confidence < minConfidenceToApply) return null

        val current = ensureInitialized()
        val record = PersonalityEvolutionRecord(
            id = UUID.randomUUID().toString(),
            timestamp = clock(),
            fromVersion = current.personalityVersion,
            toVersion = current.personalityVersion + 1,
            description = description,
            origin = origin,
            confidence = confidence,
            addedTraits = addedTraits,
            addedPreferenceKeys = addedPreferences.keys.toList()
        )
        val updated = current.copy(
            personalityVersion = record.toVersion,
            traits = (current.traits + addedTraits).distinct(),
            preferences = current.preferences + addedPreferences,
            evolutionLog = current.evolutionLog + record
        )
        repository.save(updated)
        return updated
    }

    /**
     * Reverts the most recent entry in [BeckyIdentity.evolutionLog],
     * removing exactly the traits/preference keys it added and restoring
     * the previous [BeckyIdentity.personalityVersion]. A no-op (returns
     * the current identity unchanged) when there is nothing to revert.
     */
    suspend fun revertLastEvolution(): BeckyIdentity {
        val current = ensureInitialized()
        val last = current.evolutionLog.lastOrNull() ?: return current

        val reverted = current.copy(
            personalityVersion = last.fromVersion,
            traits = current.traits.filterNot { it in last.addedTraits },
            preferences = current.preferences.filterKeys { it !in last.addedPreferenceKeys },
            evolutionLog = current.evolutionLog.dropLast(1)
        )
        repository.save(reverted)
        return reverted
    }

    companion object {
        /**
         * Default minimum [evolvePersonality] confidence required for a
         * personality change to be accepted, used when no [minConfidenceToApply]
         * is supplied to the constructor / [getInstance]. Below this, the
         * signal is discarded rather than persisted - see project spec
         * section 9 ("baja confianza nunca debe convertirse en un rasgo
         * permanente").
         *
         * This is a *default*, not a hardcoded rule: the actual threshold
         * used by any given [IdentityManager] instance is
         * [IdentityManager.minConfidenceToApply], configurable via the
         * constructor (or the [getInstance] parameter below) - see that
         * property's doc. Kept as a `const val` only so callers have a
         * named, stable default to reference instead of a magic number.
         */
        const val DEFAULT_MIN_CONFIDENCE_TO_APPLY = 0.6f

        @Volatile
        private var INSTANCE: IdentityManager? = null

        /**
         * App-wide singleton backed by [DataStoreIdentityRepository], matching
         * the [com.becky.bridge.bluetooth.BridgeRepository.getInstance] /
         * [com.becky.bridge.voice.VoiceRepository.getInstance] pattern already
         * used elsewhere in the project. Not called from anywhere yet in
         * Bloque 1 (no existing file was modified to wire this in) - reserved
         * for a future integration block (Bloque 8/9).
         *
         * @param minConfidenceToApply optional override for the singleton's
         *   evolution threshold (see [IdentityManager.minConfidenceToApply]).
         *   Only has effect the first time the singleton is created; ignored
         *   on subsequent calls since the instance is already built. Defaults
         *   to [DEFAULT_MIN_CONFIDENCE_TO_APPLY], preserving prior behavior
         *   for any existing/future caller that does not pass this argument.
         */
        fun getInstance(
            context: Context,
            minConfidenceToApply: Float = DEFAULT_MIN_CONFIDENCE_TO_APPLY
        ): IdentityManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: IdentityManager(
                    repository = DataStoreIdentityRepository(context.applicationContext),
                    minConfidenceToApply = minConfidenceToApply
                ).also { INSTANCE = it }
            }
    }
}
