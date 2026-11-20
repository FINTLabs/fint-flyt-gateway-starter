package no.novari.flyt.gateway.instance.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest().apply { requestURI = "/test" }

    @Test
    fun `returns bad request with missing field message`() {
        val cause =
            assertFailsWith<InvalidNullException> {
                jacksonObjectMapper().readValue("{}", MissingFieldPayload::class.java)
            }
        val input = MockHttpInputMessage("{}".toByteArray())
        val exception = HttpMessageNotReadableException("Invalid payload", cause, input)

        val response = handler.handleNotReadable(exception, request)

        assertEquals(400, response.statusCode.value())
        val body = assertNotNull(response.body)
        assertEquals("Mangler påkrevd felt: requiredField", body.message)
        assertEquals("/test", body.path)
    }

    @Test
    fun `unknown route gives 404, not 500`() {
        val response = handler.handleGeneric(NoResourceFoundException(HttpMethod.GET, "/test", "test"), request)

        assertEquals(404, response.statusCode.value())
        assertEquals(404, assertNotNull(response.body).status)
    }

    @Test
    fun `other Spring MVC exceptions keep their status`() {
        val response = handler.handleGeneric(HttpRequestMethodNotSupportedException("DELETE"), request)

        assertEquals(405, response.statusCode.value())
    }

    @Test
    fun `unexpected exceptions give 500`() {
        val response = handler.handleGeneric(IllegalStateException("boom"), request)

        assertEquals(500, response.statusCode.value())
        assertEquals("En uventet feil oppsto. Prøv igjen senere.", assertNotNull(response.body).message)
    }

    data class MissingFieldPayload(
        val requiredField: String,
    )
}
