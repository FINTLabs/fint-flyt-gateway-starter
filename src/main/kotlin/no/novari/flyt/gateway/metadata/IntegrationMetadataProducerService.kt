package no.novari.flyt.gateway.metadata

import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import no.novari.kafka.starter.producer.send
import no.novari.kafka.starter.topic.KafkaTopics
import org.springframework.kafka.core.KafkaTemplate

class IntegrationMetadataProducerService(
    private val kafkaTemplate: KafkaTemplate<String, IntegrationMetadata>,
    topics: KafkaTopics,
) {
    private val topic = topics.event(INTEGRATION_METADATA_RECEIVED_EVENT_NAME)

    fun publishNewIntegrationMetadata(integrationMetadata: IntegrationMetadata) {
        kafkaTemplate.send(topic, integrationMetadata)
    }

    private companion object {
        private const val INTEGRATION_METADATA_RECEIVED_EVENT_NAME = "integration-metadata-received"
    }
}
