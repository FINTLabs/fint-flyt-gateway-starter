package no.novari.flyt.gateway.instance.config

import no.novari.flyt.gateway.instance.config.properties.JacksonConfigurationProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonProperties
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.json.JsonFactoryBuilder

@AutoConfiguration(before = [JacksonAutoConfiguration::class])
@EnableConfigurationProperties(JacksonConfigurationProperties::class)
class JacksonConfiguration {
    @Bean
    fun gatewayJsonFactoryBuilderCustomizer(
        properties: JacksonConfigurationProperties,
        jacksonProperties: JacksonProperties,
    ): JsonFactoryBuilderCustomizer = GatewayJsonFactoryBuilderCustomizer(properties, jacksonProperties)

    // Boot's customizer (order 0) replaces all read constraints with spring.jackson.factory.constraints.read.*, and
    // the builder has no getter. This one runs after it, keeps Boot's values and sets the max string length.
    private class GatewayJsonFactoryBuilderCustomizer(
        private val properties: JacksonConfigurationProperties,
        private val jacksonProperties: JacksonProperties,
    ) : JsonFactoryBuilderCustomizer,
        Ordered {
        override fun customize(builder: JsonFactoryBuilder) {
            val read = jacksonProperties.factory.constraints.read
            builder.streamReadConstraints(
                StreamReadConstraints
                    .builder()
                    .maxNestingDepth(read.maxNestingDepth)
                    .maxDocumentLength(read.maxDocumentLength)
                    .maxTokenCount(read.maxTokenCount)
                    .maxNumberLength(read.maxNumberLength)
                    .maxNameLength(read.maxNameLength)
                    .maxStringLength(properties.maxStringLength())
                    .build(),
            )
        }

        override fun getOrder(): Int = 1
    }
}
