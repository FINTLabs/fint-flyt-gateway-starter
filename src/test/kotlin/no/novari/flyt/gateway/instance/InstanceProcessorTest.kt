package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.instance.exception.FileUploadException
import no.novari.flyt.gateway.instance.kafka.InstanceErrorEventProducerService
import no.novari.flyt.gateway.instance.kafka.ReceivedInstanceEventProducerService
import no.novari.flyt.gateway.instance.model.File
import no.novari.flyt.gateway.instance.model.Integration
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import no.novari.flyt.gateway.instance.validation.InstanceValidationService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.security.FlytSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

class InstanceProcessorTest {
    @Mock
    private lateinit var intgrConfClient: IntgrConfClient

    @Mock
    private lateinit var instanceValidationService: InstanceValidationService

    @Mock
    private lateinit var receivedInstanceEventProducerService: ReceivedInstanceEventProducerService

    @Mock
    private lateinit var instanceErrorEventProducerService: InstanceErrorEventProducerService

    @Mock
    private lateinit var fileClient: FileClient

    @Mock
    private lateinit var sourceApplicationIntegrationIdFunction: (Any) -> String?

    @Mock
    private lateinit var sourceApplicationInstanceIdFunction: (Any) -> String?

    @Mock
    private lateinit var instanceMapper: InstanceMapper<Any>

    @Mock
    private lateinit var instanceObject: InstanceObject

    private lateinit var instanceProcessor: InstanceProcessor<Any>

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        instanceProcessor = instanceProcessor(flytSecurityProperties(sourceApplicationId = 123L))

        val mockIntegration = mock<Integration>()
        whenever(mockIntegration.id).thenReturn(1L)
        whenever(mockIntegration.state).thenReturn(Integration.State.ACTIVE)
        whenever(intgrConfClient.getIntegration(any()))
            .thenReturn(mockIntegration)

        whenever(sourceApplicationIntegrationIdFunction.invoke(any()))
            .thenReturn("integrationId")
        whenever(sourceApplicationInstanceIdFunction.invoke(any()))
            .thenReturn("instanceId")

        whenever(instanceValidationService.validate(any())).thenReturn(null)
    }

    @Test
    fun shouldProcessInstanceAndPostFileSuccessfully() {
        val mockFile = mock<File>()
        whenever(fileClient.postFile(any<File>())).thenReturn(UUID.randomUUID())

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<(File) -> UUID>(2)
            postFile.invoke(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result = instanceProcessor.processInstance(incoming)

        assertEquals(ResponseEntity.accepted().build(), result)
        verify(fileClient, times(1)).postFile(any<File>())
        verify(receivedInstanceEventProducerService, times(1)).publish(any(), any())
    }

    @Test
    fun shouldProcessInstanceAndPostMultipleFilesSuccessfully() {
        val mockFile = mock<File>()
        whenever(fileClient.postFile(any<File>())).thenReturn(UUID.randomUUID())

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<(File) -> UUID>(2)
            postFile.invoke(mockFile)
            postFile.invoke(mockFile)
            postFile.invoke(mockFile)
            instanceObject
        }

        val incoming = Any()
        val result = instanceProcessor.processInstance(incoming)

        assertEquals(ResponseEntity.accepted().build(), result)
        verify(fileClient, times(3)).postFile(any<File>())
        verify(receivedInstanceEventProducerService, times(1)).publish(any(), eq(instanceObject))
    }

    @Test
    fun shouldProcessInstanceWithSourceApplicationIdParameter() {
        val sourceApplicationId = 456L

        whenever(instanceMapper.map(any(), any(), any())).thenReturn(instanceObject)

        val incoming = Any()
        val result = instanceProcessor.processInstance(sourceApplicationId, incoming)

        assertEquals(ResponseEntity.accepted().build(), result)
        verify(instanceMapper, times(1)).map(eq(sourceApplicationId), any(), any())
        assertThat(publishedHeaders().sourceApplicationId).isEqualTo(sourceApplicationId)
    }

    @Test
    fun `source application id comes from the gateway's configuration`() {
        whenever(instanceMapper.map(any(), any(), any())).thenReturn(instanceObject)

        instanceProcessor.processInstance(Any())

        verify(instanceMapper).map(eq(123L), any(), any())
        val headers = publishedHeaders()
        assertThat(headers.sourceApplicationId).isEqualTo(123L)
        assertThat(headers.sourceApplicationIntegrationId).isEqualTo("integrationId")
        assertThat(headers.sourceApplicationInstanceId).isEqualTo("instanceId")
        assertThat(headers.integrationId).isEqualTo(1L)
    }

    @Test
    fun `fails without a configured source application id`() {
        val processor = instanceProcessor(flytSecurityProperties(sourceApplicationId = null))

        val exception = assertThrows<IllegalStateException> { processor.processInstance(Any()) }

        assertThat(exception).hasMessageContaining("novari.flyt.security.external.source-application-id")
        verifyNoInteractions(instanceErrorEventProducerService, receivedInstanceEventProducerService)
    }

    private fun publishedHeaders(): InstanceFlowHeaders {
        val captor = argumentCaptor<InstanceFlowHeaders>()
        verify(receivedInstanceEventProducerService).publish(captor.capture(), any())
        return captor.firstValue
    }

    @Suppress("UNCHECKED_CAST")
    private fun instanceProcessor(properties: FlytSecurityProperties) =
        InstanceProcessor(
            intgrConfClient = intgrConfClient,
            instanceValidationService = instanceValidationService,
            receivedInstanceEventProducerService = receivedInstanceEventProducerService,
            instanceErrorEventProducerService = instanceErrorEventProducerService,
            flytSecurityProperties = properties,
            fileClient = fileClient,
            sourceApplicationIntegrationIdFunction = sourceApplicationIntegrationIdFunction as (Any) -> String,
            sourceApplicationInstanceIdFunction = sourceApplicationInstanceIdFunction as (Any) -> String,
            instanceMapper = instanceMapper,
        )

    @Test
    fun shouldProcessInstanceAndHandleSecondFileFailure() {
        val mockFile = mock<File>()
        val fileUploadException = FileUploadException(mockFile, "File upload failed")

        whenever(fileClient.postFile(any<File>())).thenReturn(UUID.randomUUID()).thenThrow(fileUploadException)

        whenever(instanceMapper.map(any(), any(), any())).thenAnswer { invocation ->
            val postFile = invocation.getArgument<(File) -> UUID>(2)
            postFile.invoke(mockFile)
            postFile.invoke(mockFile)
            instanceObject
        }

        val incoming = Any()
        val exception =
            assertThrows<ResponseStatusException> { instanceProcessor.processInstance(incoming) }

        assertThat(exception).hasMessageContaining("File upload failed")
    }
}
