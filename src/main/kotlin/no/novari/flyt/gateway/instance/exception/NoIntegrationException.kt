package no.novari.flyt.gateway.instance.exception

import no.novari.flyt.gateway.instance.model.SourceApplicationIdAndSourceApplicationIntegrationId

class NoIntegrationException(
    val sourceApplicationIdAndSourceApplicationIntegrationId: SourceApplicationIdAndSourceApplicationIntegrationId,
) : RuntimeException(
        "Count not find integration for ${sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationId}",
    )
