package no.novari.flyt.gateway.metadata

import no.novari.flyt.gateway.instance.flytSecurityProperties
import no.novari.flyt.gateway.metadata.model.InstanceMetadataContent
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class IntegrationMetadataProcessorTest {
    @Mock
    private lateinit var integrationMetadataProducerService: IntegrationMetadataProducerService

    private lateinit var integrationMetadataProcessor: IntegrationMetadataProcessor

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        integrationMetadataProcessor =
            IntegrationMetadataProcessor(
                integrationMetadataProducerService = integrationMetadataProducerService,
                flytSecurityProperties = flytSecurityProperties(),
            )
    }

    @Test
    fun `publishes mapped integration metadata`() {
        val incomingMetadata = IncomingMetadata(id = "form-1")
        val integrationMetadata =
            IntegrationMetadata(
                sourceApplicationId = 1L,
                sourceApplicationIntegrationId = "form-1",
                sourceApplicationIntegrationUri = null,
                integrationDisplayName = "Form 1",
                version = 1,
                instanceMetadata = InstanceMetadataContent(),
            )
        val mapper =
            object : IntegrationMetadataMapper<IncomingMetadata> {
                override fun toIntegrationMetadata(
                    sourceApplicationId: Long,
                    incomingMetadata: IncomingMetadata,
                ): IntegrationMetadata {
                    return integrationMetadata.copy(
                        sourceApplicationId = sourceApplicationId,
                        sourceApplicationIntegrationId = incomingMetadata.id,
                    )
                }
            }
        val validator =
            object : IntegrationMetadataValidator<IncomingMetadata> {
                override fun validate(incomingMetadata: IncomingMetadata): List<String>? = null
            }

        val response =
            integrationMetadataProcessor.processIntegrationMetadata(
                incomingMetadata = incomingMetadata,
                integrationMetadataMapper = mapper,
                integrationMetadataValidator = validator,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        verify(integrationMetadataProducerService).publishNewIntegrationMetadata(integrationMetadata)
    }

    @Test
    fun `throws unprocessable entity for validation errors`() {
        val mapper =
            object : IntegrationMetadataMapper<IncomingMetadata> {
                override fun toIntegrationMetadata(
                    sourceApplicationId: Long,
                    incomingMetadata: IncomingMetadata,
                ): IntegrationMetadata = error("Should not map invalid metadata")
            }
        val validator =
            object : IntegrationMetadataValidator<IncomingMetadata> {
                override fun validate(incomingMetadata: IncomingMetadata): List<String> {
                    return listOf("metadata.formId must not be blank")
                }
            }

        val exception =
            assertThrows<ResponseStatusException> {
                integrationMetadataProcessor.processIntegrationMetadata(
                    incomingMetadata = IncomingMetadata(id = ""),
                    integrationMetadataMapper = mapper,
                    integrationMetadataValidator = validator,
                )
            }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        verify(integrationMetadataProducerService, never()).publishNewIntegrationMetadata(any())
    }

    private data class IncomingMetadata(
        val id: String,
    )
}
