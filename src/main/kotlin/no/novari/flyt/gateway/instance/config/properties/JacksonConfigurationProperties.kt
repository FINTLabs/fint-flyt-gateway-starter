package no.novari.flyt.gateway.instance.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties(prefix = "novari.flyt.gateway-starter")
data class JacksonConfigurationProperties(
    val maxRequestSize: DataSize = DataSize.ofMegabytes(100),
    val jackson: Jackson = Jackson(),
) {
    fun maxStringLength(): Int = Math.toIntExact((jackson.maxStringLength ?: maxRequestSize).toBytes())

    data class Jackson(
        val maxStringLength: DataSize? = null,
    )
}
