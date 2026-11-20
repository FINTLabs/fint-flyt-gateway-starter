package no.novari.flyt.gateway.instance.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.core.StreamReadConstraints
import tools.jackson.databind.json.JsonMapper

class JacksonConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonConfiguration::class.java,
                    JacksonAutoConfiguration::class.java,
                ),
            )

    @Test
    fun `sets default Jackson max string length for large base64 payloads`() {
        contextRunner.run { context ->
            assertThat(context.readConstraints().maxStringLength).isEqualTo(104_857_600)
        }
    }

    @Test
    fun `uses max request size as default Jackson max string length`() {
        contextRunner
            .withPropertyValues("novari.flyt.gateway-starter.max-request-size=150MB")
            .run { context ->
                assertThat(context.readConstraints().maxStringLength).isEqualTo(157_286_400)
            }
    }

    @Test
    fun `allows Jackson max string length override`() {
        contextRunner
            .withPropertyValues(
                "novari.flyt.gateway-starter.max-request-size=150MB",
                "novari.flyt.gateway-starter.jackson.max-string-length=120MB",
            ).run { context ->
                assertThat(context.readConstraints().maxStringLength).isEqualTo(125_829_120)
            }
    }

    @Test
    fun `keeps the other read constraints from Spring Boot`() {
        contextRunner
            .withPropertyValues("spring.jackson.factory.constraints.read.max-nesting-depth=42")
            .run { context ->
                assertThat(context.readConstraints().maxNestingDepth).isEqualTo(42)
                assertThat(context.readConstraints().maxStringLength).isEqualTo(104_857_600)
            }
    }

    private fun AssertableApplicationContext.readConstraints(): StreamReadConstraints =
        getBean<JsonMapper>().tokenStreamFactory().streamReadConstraints()
}
