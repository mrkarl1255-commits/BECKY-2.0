package com.becky.bridge.capability

/**
 * Holds every [Capability] BECKY currently has available and lets the
 * Assistant/Becky layer discover and invoke them by id, without ever
 * knowing how each one is implemented (BLE-backed today, anything else
 * tomorrow).
 */
interface CapabilityRegistry {
    /** All capabilities currently registered, as lightweight descriptors. */
    fun descriptors(): List<CapabilityDescriptor>

    /** Runs the capability identified by [id], or [CapabilityResult.Failed] if it does not exist. */
    suspend fun execute(id: String): CapabilityResult
}

/** Default [CapabilityRegistry] backed by a fixed, in-memory list of [Capability]. */
class DefaultCapabilityRegistry(capabilities: List<Capability>) : CapabilityRegistry {

    private val byId: Map<String, Capability> = capabilities.associateBy { it.id }

    override fun descriptors(): List<CapabilityDescriptor> =
        byId.values.map { CapabilityDescriptor(it.id, it.description) }

    override suspend fun execute(id: String): CapabilityResult {
        val capability = byId[id]
            ?: return CapabilityResult.Failed("Capacidad desconocida: '$id'")
        return capability.execute()
    }
}
