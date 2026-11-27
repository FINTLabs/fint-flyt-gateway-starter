package no.novari.flyt.gateway.instance.config

import jakarta.validation.Validator
import no.novari.flyt.gateway.instance.FileClient
import no.novari.flyt.gateway.instance.HistoryClient
import no.novari.flyt.gateway.instance.InstanceProcessorFactoryService
import no.novari.flyt.gateway.instance.IntgrConfClient
import no.novari.flyt.gateway.instance.error.GlobalExceptionHandler
import no.novari.flyt.gateway.instance.error.RequestSizeLimitFilter
import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import no.novari.flyt.gateway.instance.validation.InstanceValidationErrorMappingService
import no.novari.flyt.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.gateway.metadata.IntegrationMetadataProcessor
import no.novari.flyt.gateway.metadata.IntegrationMetadataProducerService
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.security.FlytSecurityProperties
import no.novari.kafka.starter.topic.KafkaTopics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.util.unit.DataSize
import org.springframework.web.client.RestClient

@AutoConfiguration
class GatewayAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun integrationMetadataProcessor(
        integrationMetadataProducerService: IntegrationMetadataProducerService,
        flytSecurityProperties: FlytSecurityProperties,
    ): IntegrationMetadataProcessor =
        IntegrationMetadataProcessor(integrationMetadataProducerService, flytSecurityProperties)

    @Bean
    @ConditionalOnMissingBean
    fun integrationMetadataProducerService(
        kafkaTemplate: KafkaTemplate<String, IntegrationMetadata>,
        topics: KafkaTopics,
    ): IntegrationMetadataProducerService = IntegrationMetadataProducerService(kafkaTemplate, topics)

    @Bean
    @ConditionalOnMissingBean
    fun instanceProcessorFactoryService(
        intgrConfClient: IntgrConfClient,
        instanceValidationService: InstanceValidationService,
        receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
        instanceErrorEventProducerService: InstanceErrorEventProducerService,
        flytSecurityProperties: FlytSecurityProperties,
        fileClient: FileClient,
    ): InstanceProcessorFactoryService =
        InstanceProcessorFactoryService(
            intgrConfClient,
            instanceValidationService,
            receivedInstanceEventProducerService,
            instanceErrorEventProducerService,
            flytSecurityProperties,
            fileClient,
        )

    @Bean
    @ConditionalOnMissingBean
    fun historyClient(
        @Qualifier("historyRestClient")
        restClient: RestClient,
    ): HistoryClient = HistoryClient(restClient)

    @Bean
    @ConditionalOnMissingBean
    fun intgrConfClient(
        @Qualifier("intgrConfRestClient")
        restClient: RestClient,
    ): IntgrConfClient = IntgrConfClient(restClient)

    @Bean
    @ConditionalOnMissingBean
    fun fileClient(
        @Qualifier("fileRestClient")
        restClient: RestClient,
        @Value("\${novari.flyt.file-service-url}")
        fileServiceUrl: String,
    ): FileClient = FileClient(restClient, fileServiceUrl)

    @Bean
    @ConditionalOnMissingBean
    fun instanceErrorEventProducerService(
        kafkaTemplate: KafkaTemplate<String, InstanceErrorEvent>,
        topics: KafkaTopics,
        instanceValidationErrorMappingService: InstanceValidationErrorMappingService,
    ): InstanceErrorEventProducerService =
        InstanceErrorEventProducerService(kafkaTemplate, topics, instanceValidationErrorMappingService)

    @Bean
    @ConditionalOnMissingBean
    fun receivedInstanceEventProducerService(
        kafkaTemplate: KafkaTemplate<String, InstanceObject>,
        topics: KafkaTopics,
    ): ReceivedInstanceEventProducerService = ReceivedInstanceEventProducerService(kafkaTemplate, topics)

    @Bean
    @ConditionalOnMissingBean
    fun instanceValidationService(validator: Validator): InstanceValidationService =
        InstanceValidationService(validator)

    @Bean
    @ConditionalOnMissingBean
    fun instanceValidationErrorMappingService(): InstanceValidationErrorMappingService =
        InstanceValidationErrorMappingService()

    @Bean
    @ConditionalOnMissingBean
    fun requestSizeLimitFilter(
        @Value("\${novari.flyt.gateway-starter.max-request-size:100MB}") maxRequestSize: DataSize,
    ): RequestSizeLimitFilter = RequestSizeLimitFilter(maxRequestSize)

    @Bean
    @ConditionalOnMissingBean
    fun globalExceptionHandler(): GlobalExceptionHandler = GlobalExceptionHandler()
}
