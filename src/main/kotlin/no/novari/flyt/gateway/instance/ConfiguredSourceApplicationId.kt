package no.novari.flyt.gateway.instance

import no.novari.flyt.security.FlytSecurityProperties

/** The gateway's own source application, from `novari.flyt.security.external.source-application-id`. */
internal fun FlytSecurityProperties.configuredSourceApplicationId(): Long =
    checkNotNull(external.sourceApplicationId) {
        "novari.flyt.security.external.source-application-id must be set to process without a source application id"
    }
