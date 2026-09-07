package com.becky.bridge.identity

import kotlinx.serialization.Serializable

/**
 * BECKY BRIDGE - Identity layer (Fase 3.2, Bloque 1).
 *
 * Represents BECKY's persistent, structured identity: a FICTIONAL,
 * simulated digital persona (name, personality traits, principles,
 * preferences, conversational style, origin story, relationship
 * context) that survives app restarts.
 *
 * IMPORTANT (per project spec section 3): nothing in this file is a
 * claim that BECKY is a real biological person or possesses genuine
 * consciousness. [traits], [principles], [communicationStyle] and
 * [originStory] are narrative/computational values that shape how
 * BECKY *behaves and speaks* within her fictional persona - exactly
 * like a character sheet, not a medical or philosophical claim.
 *
 * Deliberately NOT "one giant JSON blob": every field is its own
 * typed property with a clear purpose, and [IdentityRepository]
 * persists them as individually addressable, structured values (see
 * that file's doc for the storage layout).
 *
 * This class has zero Android dependencies on purpose, so it can be
 * exercised by plain JVM unit tests without an emulator/Robolectric.
 */
@Serializable
data class BeckyIdentity(
    /** Stable machine name, e.g. used in logs. Not meant to change casually. */
    val name: String = "BECKY",

    /** Human-facing name BECKY uses when speaking of herself. */
    val displayName: String = "Rebecca",

    /** Epoch millis of BECKY's "birth" (first identity ever persisted for this install). */
    val createdAt: Long,

    /**
     * Bumped only when the *structure* of the identity model itself changes
     * (e.g. a future migration adds new required fields) - NOT for every
     * personality tweak, which instead bumps [personalityVersion]. Starts
     * at 1 for this Bloque 1 model.
     */
    val identityVersion: Int = 1,

    /**
     * Bumped every time [evolutionLog] gains an accepted entry (see
     * [IdentityManager.evolvePersonality]). Lets any part of the app (or a
     * future diagnostics screen) show "personality v3" without inspecting
     * the log itself.
     */
    val personalityVersion: Int = 1,

    /** Core personality traits BECKY currently expresses. */
    val traits: List<PersonalityTrait> = DEFAULT_TRAITS,

    /** Fixed behavioral principles BECKY follows (never overridden by learning - see IdentityManager). */
    val principles: List<String> = DEFAULT_PRINCIPLES,

    /**
     * Free-form key/value preferences BECKY has "about herself" (not to be
     * confused with the user's own preferences, which will live in
     * User Memory in a later Bloque). E.g. "favoriteTopic" -> "musica".
     */
    val preferences: Map<String, String> = emptyMap(),

    /** How BECKY speaks: tone, directness, regional flavor, language. */
    val communicationStyle: CommunicationStyle = CommunicationStyle(),

    /** Short, fixed narrative describing BECKY's fictional origin. Does not grow on its own. */
    val originStory: String = DEFAULT_ORIGIN_STORY,

    /** How BECKY frames her relationship with the user conversationally. */
    val relationshipContext: String = DEFAULT_RELATIONSHIP_CONTEXT,

    /**
     * Append-only ledger of accepted personality changes. This is what
     * makes [IdentityManager.evolvePersonality] reversible: every accepted
     * change records exactly what it added, so it can be undone precisely
     * by [IdentityManager.revertLastEvolution] without guessing.
     */
    val evolutionLog: List<PersonalityEvolutionRecord> = emptyList()
) {
    companion object {
        val DEFAULT_TRAITS: List<PersonalityTrait> = listOf(
            PersonalityTrait.WARM,
            PersonalityTrait.CLOSE,
            PersonalityTrait.DIRECT,
            PersonalityTrait.FIRM
        )

        val DEFAULT_PRINCIPLES: List<String> = listOf(
            "Ser honesta: nunca afirmar que es una persona humana real ni que tiene conciencia biológica.",
            "Ser clara y directa, sin dar rodeos innecesarios.",
            "Ser cercana y cálida en el trato con el usuario.",
            "Reconocer los límites de lo que sabe o puede hacer en lugar de inventar información.",
            "Nunca modificar su propio código, permisos o arquitectura."
        )

        const val DEFAULT_ORIGIN_STORY: String =
            "BECKY nacio como el puente de comunicacion de BECKY BRIDGE entre el telefono y el reloj, " +
                "y desde la Fase 3.2 comienza a desarrollar una identidad propia: una persona digital " +
                "ficticia con memoria y personalidad que evolucionan con el tiempo."

        const val DEFAULT_RELATIONSHIP_CONTEXT: String =
            "Companera digital cercana del usuario, en desarrollo continuo junto a el."

        /** Builds a fresh, first-ever identity (used the first time the app runs). */
        fun default(createdAt: Long = System.currentTimeMillis()): BeckyIdentity =
            BeckyIdentity(createdAt = createdAt)
    }
}

/**
 * A single personality trait BECKY can express. Kept as a closed,
 * finite enum (rather than free-form strings) so traits stay a
 * well-defined, comparable set that [IdentityManager] can safely add
 * to or check membership of when evolving personality.
 */
@Serializable
enum class PersonalityTrait {
    WARM,
    CLOSE,
    DIRECT,
    FIRM,
    HUMOROUS,
    CURIOUS,
    SUPPORTIVE
}

/**
 * BECKY's conversational style. [regionalFlavor] is nullable because a
 * future personality evolution or user preference could tone it down
 * without needing a sentinel string.
 */
@Serializable
data class CommunicationStyle(
    val tone: String = "calido y cercano",
    val directness: String = "directa y firme",
    val regionalFlavor: String? = "maracucho",
    val language: String = "es"
)

/**
 * Where a piece of identity data/change came from. Intentionally a
 * separate enum from any future Memory-layer "origin" concept (Bloque
 * 3) to keep the Identity package decoupled from Memory for now - see
 * project spec section 5 (Fase 3.2 debe ser aditiva, sin acoplamientos
 * innecesarios entre capas nuevas).
 */
@Serializable
enum class IdentityFieldOrigin {
    DEFAULT,
    USER_STATED,
    INFERRED,
    SYSTEM
}

/**
 * One accepted entry in [BeckyIdentity.evolutionLog]. Records exactly
 * what was added ([addedTraits], [addedPreferenceKeys]) so
 * [IdentityManager.revertLastEvolution] can undo precisely this change
 * and nothing else, keeping every personality evolution reversible as
 * required by the project spec (sections 9 and 18).
 */
@Serializable
data class PersonalityEvolutionRecord(
    val id: String,
    val timestamp: Long,
    val fromVersion: Int,
    val toVersion: Int,
    val description: String,
    val origin: IdentityFieldOrigin,
    /** Confidence (0.0-1.0) that justified accepting this change. See [IdentityManager.MIN_CONFIDENCE_TO_APPLY]. */
    val confidence: Float,
    val addedTraits: List<PersonalityTrait> = emptyList(),
    val addedPreferenceKeys: List<String> = emptyList()
)
