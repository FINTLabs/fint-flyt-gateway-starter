package no.novari.flyt.gateway.metadata

import no.novari.flyt.gateway.instance.configuredSourceApplicationId
import no.novari.flyt.security.FlytSecurityProperties
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException

class IntegrationMetadataProcessor(
    private val integrationMetadataProducerService: IntegrationMetadataProducerService,
    private val flytSecurityProperties: FlytSecurityProperties,
) {
    /** Publishes metadata for the gateway's source application, see `novari.flyt.security.external`. */
    fun <T : Any> processIntegrationMetadata(
        incomingMetadata: T,
        integrationMetadataMapper: IntegrationMetadataMapper<T>,
        integrationMetadataValidator: IntegrationMetadataValidator<T>,
    ): ResponseEntity<Void> {
        integrationMetadataValidator.validate(incomingMetadata)?.let { validationErrors ->
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validation error(s): ${validationErrors.map { "'$it'" }}",
            )
        }

        val integrationMetadata =
            integrationMetadataMapper.toIntegrationMetadata(
                flytSecurityProperties.configuredSourceApplicationId(),
                incomingMetadata,
            )

        integrationMetadataProducerService.publishNewIntegrationMetadata(integrationMetadata)

        return ResponseEntity.accepted().build()
    }
}
