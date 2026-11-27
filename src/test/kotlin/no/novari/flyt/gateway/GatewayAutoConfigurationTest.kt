package no.novari.flyt.gateway

import no.novari.flyt.gateway.instance.FileClient
import no.novari.flyt.gateway.instance.HistoryClient
import no.novari.flyt.gateway.instance.InstanceProcessorFactoryService
import no.novari.flyt.gateway.instance.IntgrConfClient
import no.novari.flyt.gateway.instance.config.GatewayAutoConfiguration
import no.novari.flyt.gateway.instance.error.GlobalExceptionHandler
import no.novari.flyt.gateway.instance.error.RequestSizeLimitFilter
import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.instance.validation.InstanceValidationErrorMappingService
import no.novari.flyt.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.gateway.metadata.IntegrationMetadataProcessor
import no.novari.flyt.gateway.metadata.IntegrationMetadataProducerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.stereotype.Component

class GatewayAutoConfigurationTest {
    private val serviceTypes =
        listOf(
            FileClient::class.java,
            HistoryClient::class.java,
            IntgrConfClient::class.java,
            InstanceProcessorFactoryService::class.java,
            InstanceErrorEventProducerService::class.java,
            ReceivedInstanceEventProducerService::class.java,
            InstanceValidationService::class.java,
            InstanceValidationErrorMappingService::class.java,
            IntegrationMetadataProcessor::class.java,
            IntegrationMetadataProducerService::class.java,
            RequestSizeLimitFilter::class.java,
        )

    // Isolate advice registration from the services' HTTP and Kafka dependencies.
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GatewayAutoConfiguration::class.java))
            .let { runner ->
                serviceTypes.fold(runner) { current, type ->
                    withMock(current, type)
                }
            }

    private fun <T : Any> withMock(
        runner: ApplicationContextRunner,
        type: Class<T>,
    ): ApplicationContextRunner = runner.withBean(type.name, type, { org.mockito.Mockito.mock(type) })

    @Test
    fun `services are not component scanned`() {
        serviceTypes.forEach { type ->
            assertThat(
                org.springframework.core.annotation.AnnotatedElementUtils
                    .hasAnnotation(type, Component::class.java),
            ).describedAs(type.name)
                .isFalse()
        }
    }

    @Test
    fun `advice is registered without scanning and supplied services are preserved`() {
        runner.run { context ->
            assertThat(context).hasNotFailed().hasSingleBean(GlobalExceptionHandler::class.java)
            serviceTypes.forEach { assertThat(context.getBeansOfType(it)).hasSize(1) }
        }
    }

    @Test
    fun `legacy component scan does not duplicate advice`() {
        runner.withUserConfiguration(LegacyAdviceScan::class.java).run { context ->
            assertThat(context).hasNotFailed().hasSingleBean(GlobalExceptionHandler::class.java)
        }
    }

    @Test
    fun `application can supply its own advice`() {
        val advice = GlobalExceptionHandler()
        runner.withBean("customAdvice", GlobalExceptionHandler::class.java, { advice }).run { context ->
            assertThat(context).hasNotFailed().hasSingleBean(GlobalExceptionHandler::class.java)
            assertThat(context.getBean<GlobalExceptionHandler>()).isSameAs(advice)
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = [GlobalExceptionHandler::class],
        excludeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [AutoConfiguration::class])],
    )
    class LegacyAdviceScan
}
