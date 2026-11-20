package no.novari.flyt.gateway.instance

import no.novari.flyt.security.FlytSecurityProperties

/** As a gateway configures `novari.flyt.security.external`. */
fun flytSecurityProperties(sourceApplicationId: Long? = 123L): FlytSecurityProperties =
    FlytSecurityProperties(
        external =
            if (sourceApplicationId == null) {
                FlytSecurityProperties.External()
            } else {
                FlytSecurityProperties.External(
                    paths = listOf("/api/test/**"),
                    sourceApplicationId = sourceApplicationId,
                    expectedClientId = "test-client",
                )
            },
    )
