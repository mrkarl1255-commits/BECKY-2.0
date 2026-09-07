package com.becky.bridge.memory

/**
 * BECKY BRIDGE - Memory layer domain models (Fase 3.2, Bloque 2).
 *
 * Pure Kotlin models with ZERO Android/Room dependencies, so they can be
 * exercised by plain JVM unit tests. [com.becky.bridge.memory.MemoryEntity]
 * (Room) is a SEPARATE type that maps to/from [MemoryEntry] - the domain
 * model here is never annotated with `@Entity` directly, keeping model,
 * persistence and management responsibilities in distinct files per the
 * project spec (section: "mantener separacion clara entre modelo,
 * repositorio y logica de gestion").
 */

/**
 * The five memory categories BECKY distinguishes. [WORKING] is
 * intentionally never persisted: it only ever exists as a
 * [WorkingMemoryItem] inside [WorkingMemoryStore] (RAM only). The other
 * four are persisted (via Room) as [MemoryEntry] rows, reachable through
 * [MemoryRepository]/[MemoryManager].
 */
enum class MemoryType {
    /** Ephemeral, RAM-only, short-lived context. Never written to Room. */
    WORKING,

    /** Concrete remembered events/interactions ("what happened"). */
    EPISODIC,

    /** General knowledge/facts learned over time ("what BECKY knows"). */
    SEMANTIC,

    /** Facts/preferences specifically about the user. */
    USER,

    /** Durable takeaways from past conversations (not raw transcripts). */
    CONVERSATIONAL
}

/**
 * Where a memory (or a candidate for one) came from. Intentionally
 * decoupled from [com.becky.bridge.identity.IdentityFieldOrigin] - a
 * separate, smaller enum for the Memory layer only, matching the
 * decoupling decision already documented in
 * [com.becky.bridge.identity.IdentityFieldOrigin]'s own KDoc.
 */
enum class MemoryOrigin {
    USER_STATED,
    INFERRED,
    SYSTEM
}

/**
 * Why a [WorkingMemoryItem] is being considered for promotion into
 * persistent memory - the three admission triggers required by the
 * project spec, evaluated by [MemoryAdmissionPolicy]. This is what makes
 * promotion a deliberate decision rather than storing everything
 * indiscriminately.
 */
enum class AdmissionReason {
    /** The user (or BECKY) explicitly stated this should be remembered. */
    EXPLICIT_STATEMENT,

    /** The same fact/preference/behavior has been observed repeatedly. */
    REPEATED_PATTERN,

    /** A single event important enough to remember on its own (e.g. first connection). */
    SIGNIFICANT_EVENT
}

/**
 * A single persisted memory entry. Carries exactly the fields required by
 * the project spec for every memory entry - [id], [timestamp],
 * [importance], [origin], [confidence], [content], [lastAccessedAt],
 * [updatable] - plus [type] (which of the four *persistent* [MemoryType]s
 * this belongs to; never [MemoryType.WORKING]) and [isDeleted] (the
 * soft-delete/"olvido" flag - never a hard row removal, so forgetting a
 * memory stays reversible via [MemoryRepository.restore]).
 *
 * @param id stable unique identifier.
 * @param type which persistent memory category this belongs to.
 * @param content the actual remembered text/fact.
 * @param timestamp epoch millis when this memory was created.
 * @param importance 0f..1f - how significant this memory is.
 * @param origin where this memory came from.
 * @param confidence 0f..1f - how confident BECKY is that [content] is accurate.
 * @param lastAccessedAt epoch millis of the last time this memory was recalled.
 * @param updatable whether [content]/fields may be edited later by
 *   [MemoryManager.update] (some memories - e.g. a fixed first-event record -
 *   may be marked non-updatable to protect their historical accuracy).
 * @param isDeleted soft-delete flag; a "forgotten" memory that can still be
 *   [MemoryRepository.restore]d.
 */
data class MemoryEntry(
    val id: String,
    val type: MemoryType,
    val content: String,
    val timestamp: Long,
    val importance: Float,
    val origin: MemoryOrigin,
    val confidence: Float,
    val lastAccessedAt: Long,
    val updatable: Boolean,
    val isDeleted: Boolean = false
)

/**
 * A single item held in [WorkingMemoryStore] - BECKY's short-lived,
 * RAM-only context. Carries the same core fields as [MemoryEntry] (minus
 * [MemoryEntry.type], which is implicitly [MemoryType.WORKING], and minus
 * [MemoryEntry.isDeleted], which is meaningless for a RAM-only item that
 * simply gets evicted/forgotten outright) so that promoting one into a
 * [MemoryEntry] (see [MemoryManager.promote]) never has to invent data.
 *
 * Per the project spec, Working Memory is NEVER automatically persisted:
 * the only way any of its content reaches Room-backed storage is through
 * [MemoryManager.promote], which always goes through
 * [MemoryAdmissionPolicy] first.
 */
data class WorkingMemoryItem(
    val id: String,
    val content: String,
    val timestamp: Long,
    val importance: Float,
    val origin: MemoryOrigin,
    val confidence: Float,
    val lastAccessedAt: Long,
    val updatable: Boolean
)

/**
 * Result of [MemoryAdmissionPolicy.evaluate]: whether a [WorkingMemoryItem]
 * is allowed to be promoted into persistent memory, and why.
 */
data class AdmissionDecision(
    val admitted: Boolean,
    val reason: AdmissionReason,
    val explanation: String
)
