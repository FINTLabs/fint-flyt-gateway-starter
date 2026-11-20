package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.security.FlytSecurityProperties

class InstanceProcessorFactoryService(
    private val intgrConfClient: IntgrConfClient,
    private val instanceValidationService: InstanceValidationService,
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceErrorEventProducerService: InstanceErrorEventProducerService,
    private val flytSecurityProperties: FlytSecurityProperties,
    private val fileClient: FileClient,
) {
    fun <T : Any> createInstanceProcessor(
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        instanceMapper: InstanceMapper<T>,
    ): InstanceProcessor<T> {
        return createInstanceProcessor(
            { _ -> sourceApplicationIntegrationId },
            sourceApplicationInstanceIdFunction,
            instanceMapper,
        )
    }

    fun <T : Any> createInstanceProcessor(
        sourceApplicationIntegrationIdFunction: (T) -> String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        instanceMapper: InstanceMapper<T>,
    ): InstanceProcessor<T> {
        return InstanceProcessor(
            intgrConfClient = intgrConfClient,
            instanceValidationService = instanceValidationService,
            receivedInstanceEventProducerService = receivedInstanceEventProducerService,
            instanceErrorEventProducerService = instanceErrorEventProducerService,
            flytSecurityProperties = flytSecurityProperties,
            fileClient = fileClient,
            sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction,
            sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction,
            instanceMapper = instanceMapper,
        )
    }

    fun <T : Any> createMultipartInstanceProcessor(
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        multipartInstanceMapper: MultipartInstanceMapper<T>,
    ): MultipartInstanceProcessor<T> {
        return createMultipartInstanceProcessor(
            { _ -> sourceApplicationIntegrationId },
            sourceApplicationInstanceIdFunction,
            multipartInstanceMapper,
        )
    }

    fun <T : Any> createMultipartInstanceProcessor(
        sourceApplicationIntegrationIdFunction: (T) -> String,
        sourceApplicationInstanceIdFunction: (T) -> String,
        multipartInstanceMapper: MultipartInstanceMapper<T>,
    ): MultipartInstanceProcessor<T> {
        return MultipartInstanceProcessor(
            intgrConfClient = intgrConfClient,
            instanceValidationService = instanceValidationService,
            receivedInstanceEventProducerService = receivedInstanceEventProducerService,
            instanceErrorEventProducerService = instanceErrorEventProducerService,
            flytSecurityProperties = flytSecurityProperties,
            fileClient = fileClient,
            sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction,
            sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction,
            multipartInstanceMapper = multipartInstanceMapper,
        )
    }
}
