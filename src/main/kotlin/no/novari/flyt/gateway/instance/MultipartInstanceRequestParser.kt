package no.novari.flyt.gateway.instance

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.server.ResponseStatusException

@Component
class MultipartInstanceRequestParser(
    private val objectMapper: ObjectMapper,
    private val validator: Validator,
) {
    fun <T : Any> parse(
        request: MultipartHttpServletRequest,
        instanceType: Class<T>,
        instancePartName: String = DEFAULT_INSTANCE_PART_NAME,
    ): MultipartInstanceRequest<T> {
        val instance = request.toInstance(instanceType, instancePartName)
        validate(instance)

        return MultipartInstanceRequest(
            instance = instance,
            multipartFiles = request.toMultipartFiles(instancePartName),
        )
    }

    private fun <T : Any> MultipartHttpServletRequest.toInstance(
        instanceType: Class<T>,
        instancePartName: String,
    ): T {
        val content =
            getFile(instancePartName)
                ?.bytes
                ?: getParameter(instancePartName)?.toByteArray()

        if (content == null || content.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Multipart request must contain non-empty '$instancePartName' part",
            )
        }

        return try {
            objectMapper.readValue(content, instanceType)
        } catch (exception: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Multipart part '$instancePartName' must contain valid JSON",
                exception,
            )
        }
    }

    private fun <T : Any> validate(instance: T) {
        val violations = validator.validate(instance)
        if (violations.isEmpty()) {
            return
        }

        val message =
            violations
                .sortedBy { it.propertyPath.toString() }
                .joinToString("; ") { "${it.propertyPath} ${it.message}" }

        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid instance: $message",
        )
    }

    private fun MultipartHttpServletRequest.toMultipartFiles(instancePartName: String) =
        multiFileMap
            .filterKeys { it != instancePartName }
            .values
            .flatten()

    private companion object {
        private const val DEFAULT_INSTANCE_PART_NAME = "instance"
    }
}
