package no.novari.flyt.gateway.instance.config

import no.novari.flyt.gateway.instance.config.properties.InstanceProcessingEventsConfigurationProperties
import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.kafka.starter.topic.EventTopicConfig
import no.novari.kafka.starter.topic.KafkaTopics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(InstanceProcessingEventsConfigurationProperties::class)
class KafkaConfiguration {
    /** Spring Boot's `KafkaAdmin` creates the topic, or updates its config, at startup. */
    @Bean
    fun flytGatewayInstanceErrorTopic(
        topics: KafkaTopics,
        properties: InstanceProcessingEventsConfigurationProperties,
    ): NewTopic =
        topics.newTopic(
            topics.errorEvent(InstanceErrorEventProducerService.ERROR_EVENT_NAME),
            EventTopicConfig(
                retention = properties.retentionTime,
                cleanupFrequency = properties.cleanupFrequency,
                partitions = properties.partitions,
            ),
        )
}
