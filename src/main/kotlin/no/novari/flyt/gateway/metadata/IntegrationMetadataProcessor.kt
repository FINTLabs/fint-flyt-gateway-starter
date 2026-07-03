package no.novari.flyt.gateway.metadata

import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class IntegrationMetadataProcessor(
    private val integrationMetadataProducerService: IntegrationMetadataProducerService,
    private val sourceApplicationAuthorizationService: SourceApplicationAuthorizationService,
) {
    fun <T : Any> processIntegrationMetadata(
        authentication: Authentication,
        incomingMetadata: T,
        integrationMetadataMapper: IntegrationMetadataMapper<T>,
        integrationMetadataValidator: IntegrationMetadataValidator<T>,
    ): ResponseEntity<Void> {
        integrationMetadataValidator.validate(incomingMetadata)?.let { validationErrors ->
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Validation error(s): ${validationErrors.map { "'$it'" }}",
            )
        }

        val integrationMetadata =
            integrationMetadataMapper.toIntegrationMetadata(
                sourceApplicationAuthorizationService.getSourceApplicationId(authentication),
                incomingMetadata,
            )

        integrationMetadataProducerService.publishNewIntegrationMetadata(integrationMetadata)

        return ResponseEntity.accepted().build()
    }
}
