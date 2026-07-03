package no.novari.flyt.gateway.metadata.model

data class InstanceValueMetadata(
    val displayName: String,
    val type: Type,
    val key: String,
) {
    enum class Type {
        STRING,
        BOOLEAN,
        FILE,
    }
}
