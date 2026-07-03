package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.instance.model.MultipartFileReference
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import java.util.UUID

interface MultipartInstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: (MultipartFileReference) -> UUID,
    ): InstanceObject
}
