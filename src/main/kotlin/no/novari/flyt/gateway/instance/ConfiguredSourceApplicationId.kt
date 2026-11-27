package no.novari.flyt.gateway.instance

import no.novari.flyt.security.FlytSecurityProperties

/** The gateway's own source application ID, from `novari.flyt.security.external.source-application`. */
internal fun FlytSecurityProperties.configuredSourceApplicationId(): Long =
    checkNotNull(external.sourceApplication) {
        "novari.flyt.security.external.source-application must be set to process without a source application id"
    }.id
