package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.instance.exception.FileUploadException
import no.novari.flyt.gateway.instance.model.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID

class FileClientRetryTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val fileClient = FileClient(builder.build(), "http://file-service")

    @Test
    fun `retries a failed upload`() {
        val fileId = UUID.randomUUID()
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        server
            .expect(requestTo(URL))
            .andRespond(withSuccess("\"$fileId\"", MediaType.APPLICATION_JSON))

        assertThat(fileClient.postFile(file())).isEqualTo(fileId)
        server.verify()
    }

    @Test
    fun `gives FileUploadException after 5 attempts`() {
        server.expect(ExpectedCount.times(5), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        val exception = assertThrows<FileUploadException> { fileClient.postFile(file()) }

        assertThat(exception).hasMessageContaining("HTTP 502")
        server.verify()
    }

    private fun file() =
        File(
            name = "a.pdf",
            sourceApplicationId = 123L,
            sourceApplicationInstanceId = "instance",
            type = MediaType.APPLICATION_PDF,
            encoding = "base64",
            base64Contents = "dGVzdA==",
        )

    private companion object {
        const val URL = "http://file-service/api/intern-klient/filer"
    }
}
