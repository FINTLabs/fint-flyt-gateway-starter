package no.novari.flyt.gateway.instance.exception

import no.novari.flyt.gateway.instance.model.Integration

class IntegrationDeactivatedException(
    val integration: Integration,
) : RuntimeException("Integration is deactivated: $integration")
