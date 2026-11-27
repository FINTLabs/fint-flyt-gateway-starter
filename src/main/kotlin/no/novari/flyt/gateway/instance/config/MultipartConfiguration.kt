package no.novari.flyt.gateway.instance.config

import jakarta.servlet.MultipartConfigElement
import no.novari.flyt.gateway.instance.config.properties.JacksonConfigurationProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.servlet.MultipartConfigFactory
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration
import org.springframework.boot.servlet.autoconfigure.MultipartProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration(before = [MultipartAutoConfiguration::class])
@ConditionalOnProperty(prefix = "spring.servlet.multipart", name = ["enabled"], matchIfMissing = true)
@EnableConfigurationProperties(
    value = [
        MultipartProperties::class,
        JacksonConfigurationProperties::class,
    ],
)
class MultipartConfiguration {
    @Bean
    @ConditionalOnMissingBean(MultipartConfigElement::class)
    fun webInstanceGatewayMultipartConfigElement(
        gatewayProperties: JacksonConfigurationProperties,
        multipartProperties: MultipartProperties,
        environment: Environment,
    ): MultipartConfigElement {
        val factory = MultipartConfigFactory()
        factory.setMaxFileSize(
            if (environment.containsProperty("spring.servlet.multipart.max-file-size")) {
                multipartProperties.maxFileSize
            } else {
                gatewayProperties.maxRequestSize
            },
        )
        factory.setMaxRequestSize(
            if (environment.containsProperty("spring.servlet.multipart.max-request-size")) {
                multipartProperties.maxRequestSize
            } else {
                gatewayProperties.maxRequestSize
            },
        )
        factory.setFileSizeThreshold(multipartProperties.fileSizeThreshold)
        multipartProperties.location
            ?.takeIf { it.isNotBlank() }
            ?.let(factory::setLocation)
        return factory.createMultipartConfig()
    }
}
