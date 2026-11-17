package no.novari.flyt.gateway.instance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the internal client API in fint-flyt-history-service.
 *
 * Returns `null` on 404.
 */
@Service
class HistoryClient(
    @param:Qualifier("historyRestClient")
    private val restClient: RestClient,
) {
    fun getArchiveInstanceId(
        sourceApplicationId: Long,
        sourceApplicationInstanceId: String,
    ): String? =
        try {
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/archive-instance-id")
                        .queryParam("sourceApplicationId", sourceApplicationId)
                        .queryParam("sourceApplicationInstanceId", "{sourceApplicationInstanceId}")
                        .build(sourceApplicationInstanceId)
                }.retrieve()
                .body<String>()
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }
}
