package no.novari.flyt.gateway.metadata

import no.novari.flyt.gateway.metadata.model.IntegrationMetadata

interface IntegrationMetadataMapper<T : Any> {
    fun toIntegrationMetadata(
        sourceApplicationId: Long,
        incomingMetadata: T,
    ): IntegrationMetadata
}
