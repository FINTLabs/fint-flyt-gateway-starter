package no.novari.flyt.gateway.metadata

import no.novari.flyt.gateway.metadata.model.InstanceMetadataContent
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException

class IntegrationMetadataProcessorTest {
    @Mock
    private lateinit var integrationMetadataProducerService: IntegrationMetadataProducerService

    @Mock
    private lateinit var sourceApplicationAuthorizationService: SourceApplicationAuthorizationService

    private lateinit var integrationMetadataProcessor: IntegrationMetadataProcessor
    private lateinit var authentication: Authentication

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        authentication = mock()
        integrationMetadataProcessor =
            IntegrationMetadataProcessor(
                integrationMetadataProducerService = integrationMetadataProducerService,
                sourceApplicationAuthorizationService = sourceApplicationAuthorizationService,
            )
    }

    @Test
    fun `publishes mapped integration metadata`() {
        val incomingMetadata = IncomingMetadata(id = "form-1")
        val integrationMetadata =
            IntegrationMetadata(
                sourceApplicationId = 123L,
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

        whenever(sourceApplicationAuthorizationService.getSourceApplicationId(authentication))
            .thenReturn(123L)

        val response =
            integrationMetadataProcessor.processIntegrationMetadata(
                authentication = authentication,
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
                    authentication = authentication,
                    incomingMetadata = IncomingMetadata(id = ""),
                    integrationMetadataMapper = mapper,
                    integrationMetadataValidator = validator,
                )
            }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        verify(sourceApplicationAuthorizationService, never()).getSourceApplicationId(any())
        verify(integrationMetadataProducerService, never()).publishNewIntegrationMetadata(any())
    }

    private data class IncomingMetadata(
        val id: String,
    )
}
