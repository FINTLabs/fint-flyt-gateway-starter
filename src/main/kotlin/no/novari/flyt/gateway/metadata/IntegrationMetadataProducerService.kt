package no.novari.flyt.gateway.metadata

import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import no.novari.kafka.producing.ParameterizedProducerRecord
import no.novari.kafka.producing.ParameterizedTemplate
import no.novari.kafka.producing.ParameterizedTemplateFactory
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import org.springframework.stereotype.Service

@Service
class IntegrationMetadataProducerService(
    templateFactory: ParameterizedTemplateFactory,
) {
    private val template: ParameterizedTemplate<IntegrationMetadata> =
        templateFactory.createTemplate(IntegrationMetadata::class.java)

    private val integrationMetadataEventTopicNameParameters =
        EventTopicNameParameters
            .builder()
            .topicNamePrefixParameters(
                TopicNamePrefixParameters
                    .stepBuilder()
                    .orgIdApplicationDefault()
                    .domainContextApplicationDefault()
                    .build(),
            ).eventName(INTEGRATION_METADATA_RECEIVED_EVENT_NAME)
            .build()

    fun publishNewIntegrationMetadata(integrationMetadata: IntegrationMetadata) {
        template.send(
            ParameterizedProducerRecord
                .builder<IntegrationMetadata>()
                .topicNameParameters(integrationMetadataEventTopicNameParameters)
                .value(integrationMetadata)
                .build(),
        )
    }

    private companion object {
        private const val INTEGRATION_METADATA_RECEIVED_EVENT_NAME = "integration-metadata-received"
    }
}
