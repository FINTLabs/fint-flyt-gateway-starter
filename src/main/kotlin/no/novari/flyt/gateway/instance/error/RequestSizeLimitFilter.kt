package no.novari.flyt.gateway.instance.error

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.unit.DataSize
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets

@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestSizeLimitFilter(
    @Value("\${novari.flyt.gateway-starter.max-request-size:100MB}") maxInMemorySize: DataSize,
) : OncePerRequestFilter() {
    private val maxInMemoryBytes = maxInMemorySize.toBytes()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val contentLength = request.contentLengthLong
        if (contentLength > 0 && maxInMemoryBytes > 0 && contentLength > maxInMemoryBytes) {
            val url = ErrorResponseUtils.resolveFullUrl(request)
            log.warn(
                "Payload too large for {} {}: content-length={} max-in-memory={}",
                request.method,
                url,
                contentLength,
                maxInMemoryBytes,
            )
            discardRequestBody(request)
            response.status = HttpStatus.CONTENT_TOO_LARGE.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = StandardCharsets.UTF_8.name()
            response.outputStream.use { output ->
                output.write(ErrorResponseUtils.payloadTooLargeJson)
                output.flush()
            }
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun discardRequestBody(request: HttpServletRequest) {
        try {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val input = request.inputStream
            while (input.read(buffer) != -1) {
                // Discard
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RequestSizeLimitFilter::class.java)
    }
}
