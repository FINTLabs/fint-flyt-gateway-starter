package no.novari.flyt.gateway.instance.exception

import no.novari.flyt.gateway.instance.model.File

class FileUploadException(
    val file: File,
    postResponse: String,
    cause: Throwable? = null,
) : RuntimeException("Could not post file=$file. POST response='$postResponse'", cause)
