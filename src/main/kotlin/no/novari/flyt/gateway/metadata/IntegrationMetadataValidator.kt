package no.novari.flyt.gateway.metadata

interface IntegrationMetadataValidator<T : Any> {
    fun validate(incomingMetadata: T): List<String>?
}
