package no.novari.flyt.gateway.instance

import no.novari.flyt.gateway.instance.model.File
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import java.util.UUID

interface InstanceMapper<T> {
    fun map(
        sourceApplicationId: Long,
        incomingInstance: T,
        persistFile: (File) -> UUID,
    ): InstanceObject
}
