package no.novari.flyt.gateway.instance.config.properties

import no.novari.kafka.topic.configuration.EventCleanupFrequency
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "novari.flyt.gateway-starter.kafka.topic.instance-error")
data class InstanceProcessingEventsConfigurationProperties(
    val retentionTime: Duration = Duration.ofDays(4),
    val cleanupFrequency: EventCleanupFrequency = EventCleanupFrequency.NORMAL,
    val partitions: Int = 1,
)
