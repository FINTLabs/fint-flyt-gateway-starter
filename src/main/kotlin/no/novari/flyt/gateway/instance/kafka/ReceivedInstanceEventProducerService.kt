package no.novari.flyt.gateway.instance.kafka

import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.producing.send
import no.novari.kafka.starter.topic.KafkaTopics
import org.springframework.kafka.core.KafkaTemplate

class ReceivedInstanceEventProducerService(
    private val kafkaTemplate: KafkaTemplate<String, InstanceObject>,
    topics: KafkaTopics,
) {
    private val topic = topics.event(EVENT_NAME)

    fun publish(
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObject,
    ) {
        kafkaTemplate.send(topic, instanceFlowHeaders, instance)
    }

    private companion object {
        private const val EVENT_NAME = "instance-received"
    }
}
