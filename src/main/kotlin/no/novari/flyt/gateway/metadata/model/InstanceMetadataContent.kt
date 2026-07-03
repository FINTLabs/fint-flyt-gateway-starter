package no.novari.flyt.gateway.metadata.model

data class InstanceMetadataContent(
    val instanceValueMetadata: List<InstanceValueMetadata> = emptyList(),
    val instanceObjectCollectionMetadata: List<InstanceObjectCollectionMetadata> = emptyList(),
    val categories: List<InstanceMetadataCategory> = emptyList(),
)
