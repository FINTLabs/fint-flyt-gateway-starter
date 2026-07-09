package no.novari.flyt.gateway.instance

import org.springframework.web.multipart.MultipartFile

data class MultipartInstanceRequest<T : Any>(
    val instance: T,
    val multipartFiles: Collection<MultipartFile>,
)
