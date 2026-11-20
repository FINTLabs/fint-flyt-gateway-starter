package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.instance.exception.AbstractInstanceRejectedException
import no.novari.flyt.gateway.instance.exception.FileUploadException
import no.novari.flyt.gateway.instance.exception.IntegrationDeactivatedException
import no.novari.flyt.gateway.instance.exception.NoIntegrationException
import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.instance.model.Integration
import no.novari.flyt.gateway.instance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import no.novari.flyt.gateway.instance.validation.InstanceValidationException
import no.novari.flyt.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.security.FlytSecurityProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class InstanceProcessor<T : Any>(
    private val intgrConfClient: IntgrConfClient,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceErrorEventProducerService: InstanceErrorEventProducerService,
    private val flytSecurityProperties: FlytSecurityProperties,
    private val fileClient: FileClient,
    private val sourceApplicationIntegrationIdFunction: (T) -> String,
    private val sourceApplicationInstanceIdFunction: (T) -> String,
    private val instanceMapper: InstanceMapper<T>,
) {
    @Value("\${novari.flyt.gateway-starter.check-integration-exists:true}")
    var checkIntegrationExists: Boolean = true

    /** Processes an instance from the gateway's source application, see `novari.flyt.security.external`. */
    fun processInstance(incomingInstance: T): ResponseEntity<Void> =
        processInstance(flytSecurityProperties.configuredSourceApplicationId(), incomingInstance)

    fun processInstance(
        sourceApplicationId: Long,
        incomingInstance: T,
    ): ResponseEntity<Void> {
        val fileIds = mutableListOf<UUID>()
        var headers =
            InstanceFlowHeaders(
                sourceApplicationId = sourceApplicationId,
                correlationId = UUID.randomUUID(),
                fileIds = fileIds,
            )

        try {
            val sourceApplicationIntegrationId = sourceApplicationIntegrationIdFunction(incomingInstance)
            val sourceApplicationInstanceId = sourceApplicationInstanceIdFunction(incomingInstance)

            headers =
                headers.copy(
                    sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                    sourceApplicationInstanceId = sourceApplicationInstanceId,
                )

            instanceValidationService.validate(incomingInstance)?.let {
                throw InstanceValidationException(it)
            }

            require(sourceApplicationIntegrationId.isNotBlank()) {
                "sourceApplicationIntegrationId is blank, and was not caught in validation"
            }
            require(sourceApplicationInstanceId.isNotBlank()) {
                "sourceApplicationInstanceId is blank, and was not caught in validation"
            }

            val idPair =
                SourceApplicationIdAndSourceApplicationIntegrationId(
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                )

            if (checkIntegrationExists) {
                val integration =
                    intgrConfClient
                        .getIntegration(idPair)
                        ?: throw NoIntegrationException(idPair)
                headers = headers.copy(integrationId = integration.id)
                if (integration.state == Integration.State.DEACTIVATED) {
                    throw IntegrationDeactivatedException(integration)
                }
            }
        } catch (e: InstanceValidationException) {
            instanceErrorEventProducerService.publishInstanceValidationErrorEvent(headers, e)
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                buildString {
                    append("Validation error: ")
                    append(e.validationErrors.joinToString { "'${it.fieldPath} ${it.errorMessage}'" })
                },
            )
        } catch (e: NoIntegrationException) {
            instanceErrorEventProducerService.publishNoIntegrationFoundErrorEvent(headers, e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.message)
        } catch (e: IntegrationDeactivatedException) {
            instanceErrorEventProducerService.publishIntegrationDeactivatedErrorEvent(headers, e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.message)
        } catch (e: RuntimeException) {
            instanceErrorEventProducerService.publishGeneralSystemErrorEvent(headers)
            throw e
        }

        return try {
            val instanceObject =
                instanceMapper.map(
                    sourceApplicationId,
                    incomingInstance,
                ) { file ->
                    fileClient
                        .postFile(file)
                        .also(fileIds::add)
                }
            receivedInstanceEventProducerService.publish(headers, instanceObject)
            ResponseEntity.accepted().build()
        } catch (e: AbstractInstanceRejectedException) {
            log.error("Instance receival error", e)
            instanceErrorEventProducerService.publishInstanceRejectedErrorEvent(headers, e)
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.message, e)
        } catch (e: FileUploadException) {
            log.error("File upload error", e)
            instanceErrorEventProducerService.publishInstanceFileUploadErrorEvent(headers, e)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
        } catch (e: Exception) {
            log.error("General system error", e)
            instanceErrorEventProducerService.publishGeneralSystemErrorEvent(headers)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "General system error", e)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
