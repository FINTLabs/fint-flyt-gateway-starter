package no.novari.flyt.gateway.instance.kafka

import no.novari.flyt.gateway.instance.ErrorCode
import no.novari.flyt.gateway.instance.exception.AbstractInstanceRejectedException
import no.novari.flyt.gateway.instance.exception.FileUploadException
import no.novari.flyt.gateway.instance.exception.IntegrationDeactivatedException
import no.novari.flyt.gateway.instance.exception.MultipartFileUploadException
import no.novari.flyt.gateway.instance.exception.NoIntegrationException
import no.novari.flyt.gateway.instance.validation.InstanceValidationErrorMappingService
import no.novari.flyt.gateway.instance.validation.InstanceValidationException
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.send
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
import no.novari.kafka.starter.topic.KafkaTopics
import org.springframework.kafka.core.KafkaTemplate

/** The topic is created by `KafkaConfiguration`. */
class InstanceErrorEventProducerService(
    private val kafkaTemplate: KafkaTemplate<String, InstanceErrorEvent>,
    topics: KafkaTopics,
    private val instanceValidationErrorMappingService: InstanceValidationErrorMappingService,
) {
    private val topic = topics.errorEvent(ERROR_EVENT_NAME)

    private fun send(
        headers: InstanceFlowHeaders,
        errors: ErrorCollection,
    ) {
        kafkaTemplate.send(topic, headers, InstanceErrorEvent(name = InstanceErrorOrigin.RECEIVAL, errors = errors))
    }

    private fun singleError(
        code: ErrorCode,
        args: Map<String, String?> = emptyMap(),
    ): ErrorCollection = ErrorCollection(Error(errorCode = code.getCode(), args = args))

    fun publishInstanceValidationErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: InstanceValidationException,
    ) {
        send(instanceFlowHeaders, instanceValidationErrorMappingService.map(e))
    }

    fun publishInstanceRejectedErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: AbstractInstanceRejectedException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.INSTANCE_REJECTED_ERROR,
                mapOf("message" to e.message.orEmpty()),
            ),
        )
    }

    fun publishInstanceFileUploadErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: FileUploadException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.FILE_UPLOAD_ERROR,
                mapOf(
                    "name" to e.file.name,
                    "mediatype" to e.file.type.toString(),
                ),
            ),
        )
    }

    fun publishMultipartFileUploadErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: MultipartFileUploadException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.FILE_UPLOAD_ERROR,
                mapOf(
                    "name" to e.file.name,
                    "mediatype" to e.file.type.toString(),
                ),
            ),
        )
    }

    fun publishNoIntegrationFoundErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: NoIntegrationException,
    ) {
        val id = e.sourceApplicationIdAndSourceApplicationIntegrationId
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.NO_INTEGRATION_FOUND_ERROR,
                mapOf(
                    "sourceApplicationId" to id.sourceApplicationId.toString(),
                    "sourceApplicationIntegrationId" to id.sourceApplicationIntegrationId,
                ),
            ),
        )
    }

    fun publishIntegrationDeactivatedErrorEvent(
        instanceFlowHeaders: InstanceFlowHeaders,
        e: IntegrationDeactivatedException,
    ) {
        send(
            instanceFlowHeaders,
            singleError(
                ErrorCode.INTEGRATION_DEACTIVATED_ERROR,
                mapOf(
                    "sourceApplicationId" to e.integration.sourceApplicationId.toString(),
                    "sourceApplicationIntegrationId" to e.integration.sourceApplicationIntegrationId,
                ),
            ),
        )
    }

    fun publishGeneralSystemErrorEvent(instanceFlowHeaders: InstanceFlowHeaders) {
        send(instanceFlowHeaders, singleError(ErrorCode.GENERAL_SYSTEM_ERROR))
    }

    internal companion object {
        internal const val ERROR_EVENT_NAME = "instance-error"
    }
}
