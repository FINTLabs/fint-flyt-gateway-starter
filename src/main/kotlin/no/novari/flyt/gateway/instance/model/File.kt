package no.novari.flyt.gateway.instance.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.ser.std.ToStringSerializer

data class File(
    val name: String,
    val sourceApplicationId: Long,
    val sourceApplicationInstanceId: String,
    @field:JsonSerialize(using = ToStringSerializer::class)
    val type: MediaType,
    val encoding: String,
    @field:JsonProperty("contents")
    val base64Contents: String,
) {
    override fun toString(): String {
        return buildString {
            append("${File::class.java.simpleName}[")
            append("sourceApplicationId=").append(sourceApplicationId).append(", ")
            append("sourceApplicationInstanceId='").append(sourceApplicationInstanceId).append("', ")
            append("type=").append(type).append(", ")
            append("encoding='").append(encoding).append("'")
            append("]")
        }
    }
}
