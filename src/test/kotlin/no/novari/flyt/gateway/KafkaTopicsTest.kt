package no.novari.flyt.gateway

import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import no.novari.flyt.gateway.metadata.IntegrationMetadataProducerService
import no.novari.flyt.gateway.metadata.model.InstanceMetadataContent
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import no.novari.flyt.kafka.instanceflow.headers.INSTANCE_FLOW_HEADERS_KEY
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.headers.instanceFlowHeaders
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.TestConstructor
import java.time.Duration
import java.util.UUID

/** Topic names and group ids must be the same as with fint-kafka 6 (flyt-gateway-starter 5). */
@SpringBootTest
@EmbeddedKafka
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class KafkaTopicsTest(
    private val receivedInstanceEventProducerService: ReceivedInstanceEventProducerService,
    private val instanceErrorEventProducerService: InstanceErrorEventProducerService,
    private val integrationMetadataProducerService: IntegrationMetadataProducerService,
    private val listenerEndpointRegistry: KafkaListenerEndpointRegistry,
    private val kafkaAdmin: KafkaAdmin,
    private val newTopics: List<NewTopic>,
    private val broker: EmbeddedKafkaBroker,
) {
    private val headers =
        InstanceFlowHeaders(
            sourceApplicationId = 123L,
            correlationId = UUID.randomUUID(),
            sourceApplicationIntegrationId = "integration",
            sourceApplicationInstanceId = "instance",
        )

    @Test
    fun `instance-received has the same topic name, the instance flow headers and the JSON value`() {
        receivedInstanceEventProducerService.publish(headers, InstanceObject(valuePerKey = mapOf("a" to "b")))

        val record = singleRecord("test-org.flyt.event.instance-received")

        assertThat(record.headers().instanceFlowHeaders()).isEqualTo(headers)
        assertThat(String(record.headers().lastHeader("origin.application.id").value())).isEqualTo("test-gateway")
        assertThat(record.value()).isEqualTo("""{"valuePerKey":{"a":"b"},"objectCollectionPerKey":{}}""")
    }

    @Test
    fun `instance-error has the same topic name and the instance flow headers`() {
        instanceErrorEventProducerService.publishGeneralSystemErrorEvent(headers)

        val record = singleRecord("test-org.flyt.event.error.instance-error")

        assertThat(record.headers().lastHeader(INSTANCE_FLOW_HEADERS_KEY)).isNotNull()
        assertThat(record.headers().instanceFlowHeaders()).isEqualTo(headers)
        assertThat(record.value())
            .isEqualTo(
                """{"name":"RECEIVAL","errors":{"errors":[{"errorCode":""" +
                    """"FINT_FLYT_INSTANCE_GATEWAY_GENERAL_SYSTEM_ERROR","args":{}}]}}""",
            )
    }

    @Test
    fun `integration-metadata-received has the same topic name`() {
        integrationMetadataProducerService.publishNewIntegrationMetadata(
            IntegrationMetadata(
                sourceApplicationId = 123L,
                sourceApplicationIntegrationId = "integration",
                sourceApplicationIntegrationUri = null,
                integrationDisplayName = "Integration",
                version = 1,
                instanceMetadata = InstanceMetadataContent(),
            ),
        )

        val record = singleRecord("test-org.flyt.event.integration-metadata-received")

        assertThat(record.value()).contains(""""sourceApplicationIntegrationId":"integration"""")
    }

    @Test
    fun `instance-error topic is created with the same config as before`() {
        val topic = newTopics.single()

        assertThat(topic.name()).isEqualTo("test-org.flyt.event.error.instance-error")
        assertThat(topic.numPartitions()).isEqualTo(1)
        assertThat(topic.configs())
            .containsEntry("cleanup.policy", "delete")
            .containsEntry("retention.ms", Duration.ofDays(4).toMillis().toString())
            .containsEntry("segment.ms", Duration.ofHours(12).toMillis().toString())
        assertThat(kafkaAdmin.describeTopics(topic.name())).containsKey(topic.name())
    }

    @Test
    fun `the starter has no listeners, so the gateways' group ids are their own`() {
        assertThat(listenerEndpointRegistry.listenerContainers).isEmpty()
    }

    private fun singleRecord(topic: String): ConsumerRecord<String, String> =
        KafkaConsumer(
            KafkaTestUtils.consumerProps(broker, "test-reader-$topic", false),
            StringDeserializer(),
            StringDeserializer(),
        ).use { consumer ->
            consumer.subscribe(listOf(topic))
            KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(30))
        }
}
