package no.novari.flyt.gateway.instance.exception

import no.novari.flyt.gateway.instance.model.MultipartFileUpload

class MultipartFileUploadException(
    val file: MultipartFileUpload,
    postResponse: String,
    cause: Throwable? = null,
) : RuntimeException("Could not post file=$file. POST response='$postResponse'", cause)
