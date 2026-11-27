package no.novari.flyt.gateway.instance

import no.novari.flyt.security.FlytSecurityProperties
import no.novari.flyt.security.SourceApplication

/** As a gateway configures `novari.flyt.security.external`. */
fun flytSecurityProperties(sourceApplication: SourceApplication? = SourceApplication.ACOS): FlytSecurityProperties =
    FlytSecurityProperties(
        external =
            if (sourceApplication == null) {
                FlytSecurityProperties.External()
            } else {
                FlytSecurityProperties.External(
                    paths = listOf("/api/test/**"),
                    sourceApplication = sourceApplication,
                )
            },
    )
