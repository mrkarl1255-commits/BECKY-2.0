package com.becky.bridge.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit tests for [MemoryAdmissionPolicy] (Fase 3.2, Bloque 2).
 * No Android/Room dependency needed - only JUnit4 (already a project
 * dependency).
 */
class MemoryAdmissionPolicyTest {

    private fun candidate(
        origin: MemoryOrigin = MemoryOrigin.INFERRED,
        confidence: Float = 0.9f,
        importance: Float = 0.1f
    ) = WorkingMemoryItem(
        id = "wm-1",
        content = "some content",
        timestamp = 1_000L,
        importance = importance,
        origin = origin,
        confidence = confidence,
        lastAccessedAt = 1_000L,
        updatable = true
    )

    @Test
    fun `explicit user-stated content with sufficient confidence is admitted`() {
        val policy = MemoryAdmissionPolicy()
        val decision = policy.evaluate(
            candidate(origin = MemoryOrigin.USER_STATED, confidence = 0.6f),
            existingSimilarCount = 0
        )

        assertTrue(decision.admitted)
        assertEquals(AdmissionReason.EXPLICIT_STATEMENT, decision.reason)
    }

    @Test
    fun `below minimum confidence is always rejected regardless of trigger`() {
        val policy = MemoryAdmissionPolicy(minConfidenceToAdmit = 0.5f)
        val decision = policy.evaluate(
            candidate(origin = MemoryOrigin.USER_STATED, confidence = 0.2f, importance = 1.0f),
            existingSimilarCount = 10
        )

        assertFalse("low confidence must reject even an explicit statement or a high repeat count", decision.admitted)
    }

    @Test
    fun `repeated pattern reaching the threshold is admitted`() {
        val policy = MemoryAdmissionPolicy(repeatedPatternThreshold = 3)
        // existingSimilarCount=2 + this one = 3rd observation -> reaches threshold.
        val decision = policy.evaluate(
            candidate(origin = MemoryOrigin.INFERRED, confidence = 0.9f, importance = 0.1f),
            existingSimilarCount = 2
        )

        assertTrue(decision.admitted)
        assertEquals(AdmissionReason.REPEATED_PATTERN, decision.reason)
    }

    @Test
    fun `repeated pattern below the threshold is rejected`() {
        val policy = MemoryAdmissionPolicy(repeatedPatternThreshold = 3)
        val decision = policy.evaluate(
            candidate(origin = MemoryOrigin.INFERRED, confidence = 0.9f, importance = 0.1f),
            existingSimilarCount = 1 // 1 + this one = 2nd observation, below threshold 3
        )

        assertFalse(decision.admitted)
    }

    @Test
    fun `significant event importance reaching the threshold is admitted`() {
        val policy = MemoryAdmissionPolicy(significantEventThreshold = 0.8f)
        val decision = policy.evaluate(
            candidate(origin = MemoryOrigin.SYSTEM, confidence = 0.9f, importance = 0.85f),
            existingSimilarCount = 0
        )

        assertTrue(decision.admitted)
        assertEquals(AdmissionReason.SIGNIFICANT_EVENT, decision.reason)
    }

    @Test
    fun `nothing satisfied is rejected`() {
        val policy = MemoryAdmissionPolicy()
        val decision = policy.evaluate(
            candidate(origin = MemoryOrigin.INFERRED, confidence = 0.9f, importance = 0.1f),
            existingSimilarCount = 0
        )

        assertFalse(decision.admitted)
    }

    @Test
    fun `thresholds are configurable per instance, independent of the defaults`() {
        val strict = MemoryAdmissionPolicy(significantEventThreshold = 0.99f)
        val lenient = MemoryAdmissionPolicy(significantEventThreshold = 0.2f)

        val sameCandidate = candidate(origin = MemoryOrigin.SYSTEM, confidence = 0.9f, importance = 0.5f)

        assertFalse(
            "0.5 must be rejected when significantEventThreshold=0.99",
            strict.evaluate(sameCandidate, existingSimilarCount = 0).admitted
        )
        assertTrue(
            "0.5 must be admitted when significantEventThreshold=0.2",
            lenient.evaluate(sameCandidate, existingSimilarCount = 0).admitted
        )
    }
}
