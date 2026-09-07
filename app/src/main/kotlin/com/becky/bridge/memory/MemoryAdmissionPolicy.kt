package com.becky.bridge.memory

/**
 * Decides whether a [WorkingMemoryItem] may be promoted into persistent
 * memory (Fase 3.2, Bloque 2) - the deliberate "admission mechanism" the
 * project spec requires so BECKY does NOT store everything indiscriminately.
 *
 * A candidate is admitted only when at least one of the three approved
 * triggers holds:
 * - [AdmissionReason.EXPLICIT_STATEMENT]: the candidate's
 *   [WorkingMemoryItem.origin] is [MemoryOrigin.USER_STATED] (someone
 *   explicitly said this should be remembered) AND its confidence clears
 *   [minConfidenceToAdmit].
 * - [AdmissionReason.REPEATED_PATTERN]: [existingSimilarCount] (how many
 *   times equivalent content has already been observed, computed by the
 *   caller - see [MemoryManager.promote]) reaches [repeatedPatternThreshold].
 * - [AdmissionReason.SIGNIFICANT_EVENT]: [WorkingMemoryItem.importance]
 *   reaches [significantEventThreshold].
 *
 * Every threshold is a constructor parameter (never hardcoded inside
 * [evaluate]) - the same configuration approach already corrected in
 * [com.becky.bridge.identity.IdentityManager] (`minConfidenceToApply`) -
 * so callers can tune how conservative admission is without editing this
 * class or coupling Memory to any external config store.
 */
class MemoryAdmissionPolicy(
    private val minConfidenceToAdmit: Float = DEFAULT_MIN_CONFIDENCE_TO_ADMIT,
    private val repeatedPatternThreshold: Int = DEFAULT_REPEATED_PATTERN_THRESHOLD,
    private val significantEventThreshold: Float = DEFAULT_SIGNIFICANT_EVENT_THRESHOLD
) {

    /**
     * @param candidate the working-memory item being considered for promotion.
     * @param existingSimilarCount how many prior observations of equivalent
     *   content already exist (0 if this is the first time). Computing
     *   "similar" is the caller's responsibility (e.g. exact/normalized
     *   content match against active memories) - this policy only compares
     *   the resulting count against [repeatedPatternThreshold].
     */
    fun evaluate(candidate: WorkingMemoryItem, existingSimilarCount: Int): AdmissionDecision {
        if (candidate.confidence < minConfidenceToAdmit) {
            return AdmissionDecision(
                admitted = false,
                reason = AdmissionReason.SIGNIFICANT_EVENT,
                explanation = "confidence ${candidate.confidence} below minimum $minConfidenceToAdmit - rejected regardless of trigger"
            )
        }

        if (candidate.origin == MemoryOrigin.USER_STATED) {
            return AdmissionDecision(
                admitted = true,
                reason = AdmissionReason.EXPLICIT_STATEMENT,
                explanation = "explicitly stated by the user/system (origin=USER_STATED) with sufficient confidence"
            )
        }

        if (existingSimilarCount + 1 >= repeatedPatternThreshold) {
            return AdmissionDecision(
                admitted = true,
                reason = AdmissionReason.REPEATED_PATTERN,
                explanation = "observed ${existingSimilarCount + 1} times, reaching the repeated-pattern threshold ($repeatedPatternThreshold)"
            )
        }

        if (candidate.importance >= significantEventThreshold) {
            return AdmissionDecision(
                admitted = true,
                reason = AdmissionReason.SIGNIFICANT_EVENT,
                explanation = "importance ${candidate.importance} reaches the significant-event threshold ($significantEventThreshold)"
            )
        }

        return AdmissionDecision(
            admitted = false,
            reason = AdmissionReason.SIGNIFICANT_EVENT,
            explanation = "no admission trigger satisfied (not explicit, not a repeated pattern, not significant enough)"
        )
    }

    companion object {
        /** Default minimum confidence required for ANY admission trigger to apply. */
        const val DEFAULT_MIN_CONFIDENCE_TO_ADMIT = 0.5f

        /** Default number of observations (including the current one) needed for [AdmissionReason.REPEATED_PATTERN]. */
        const val DEFAULT_REPEATED_PATTERN_THRESHOLD = 3

        /** Default importance needed for [AdmissionReason.SIGNIFICANT_EVENT] on its own. */
        const val DEFAULT_SIGNIFICANT_EVENT_THRESHOLD = 0.8f
    }
}
