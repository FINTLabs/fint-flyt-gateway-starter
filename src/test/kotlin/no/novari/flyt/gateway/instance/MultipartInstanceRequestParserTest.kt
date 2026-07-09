package no.novari.flyt.gateway.instance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.web.server.ResponseStatusException

class MultipartInstanceRequestParserTest {
    private val parser =
        MultipartInstanceRequestParser(
            objectMapper = jacksonObjectMapper(),
            validator = Validation.buildDefaultValidatorFactory().validator,
        )

    @Test
    fun shouldParseOctetStreamInstancePartAndExcludeItFromMultipartFiles() {
        val request =
            MockMultipartHttpServletRequest().apply {
                addFile(
                    MockMultipartFile(
                        "instance",
                        "instance.json",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        """
                        {
                          "metadata": {
                            "formId": "eapply-journalpost",
                            "instanceId": "instance-123"
                          },
                          "elements": [
                            {
                              "id": "Journalpost.Title",
                              "value": "Soknad"
                            }
                          ]
                        }
                        """.trimIndent().toByteArray(),
                    ),
                )
                addFile(
                    MockMultipartFile(
                        "mainDocument",
                        "soknad.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "pdf-content".toByteArray(),
                    ),
                )
            }

        val result = parser.parse(request, TestInstance::class.java)

        assertThat(result.instance.metadata?.formId).isEqualTo("eapply-journalpost")
        assertThat(result.instance.metadata?.instanceId).isEqualTo("instance-123")
        assertThat(result.multipartFiles.map { it.name }).containsExactly("mainDocument")
    }

    @Test
    fun shouldParseTextInstancePart() {
        val request =
            MockMultipartHttpServletRequest().apply {
                addParameter(
                    "instance",
                    """
                    {
                      "metadata": {
                        "formId": "eapply-case",
                        "instanceId": "instance-124"
                      },
                      "elements": [
                        {
                          "id": "Case.Title",
                          "value": "Soknad"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        val result = parser.parse(request, TestInstance::class.java)

        assertThat(result.instance.metadata?.formId).isEqualTo("eapply-case")
        assertThat(result.multipartFiles).isEmpty()
    }

    @Test
    fun shouldReturnBadRequestWhenInstancePartIsMissing() {
        val exception =
            assertThrows<ResponseStatusException> {
                parser.parse(MockMultipartHttpServletRequest(), TestInstance::class.java)
            }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.reason).contains("non-empty 'instance' part")
    }

    @Test
    fun shouldReturnBadRequestWhenInstancePartContainsInvalidJson() {
        val request =
            MockMultipartHttpServletRequest().apply {
                addFile(
                    MockMultipartFile(
                        "instance",
                        "instance.json",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        "{".toByteArray(),
                    ),
                )
            }

        val exception =
            assertThrows<ResponseStatusException> {
                parser.parse(request, TestInstance::class.java)
            }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.reason).contains("valid JSON")
    }

    @Test
    fun shouldReturnBadRequestWhenInstancePartFailsValidation() {
        val request =
            MockMultipartHttpServletRequest().apply {
                addParameter(
                    "instance",
                    """
                    {
                      "metadata": {
                        "formId": "",
                        "instanceId": "instance-124"
                      },
                      "elements": []
                    }
                    """.trimIndent(),
                )
            }

        val exception =
            assertThrows<ResponseStatusException> {
                parser.parse(request, TestInstance::class.java)
            }

        assertThat(exception.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.reason).contains("Invalid instance")
        assertThat(exception.reason).contains("metadata.formId")
        assertThat(exception.reason).contains("elements")
    }

    data class TestInstance(
        @field:NotNull
        @field:Valid
        val metadata: TestMetadata? = null,
        @field:NotEmpty
        @field:Valid
        val elements: List<@Valid TestElement>? = null,
    )

    data class TestMetadata(
        @field:NotBlank
        val formId: String? = null,
        @field:NotBlank
        val instanceId: String? = null,
    )

    data class TestElement(
        @field:NotBlank
        val id: String? = null,
        val value: JsonNode? = null,
    )
}
