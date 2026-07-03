package no.novari.flyt.gateway.metadata.model

data class InstanceObjectCollectionMetadata(
    val displayName: String,
    val objectMetadata: InstanceMetadataContent,
    val key: String,
)
