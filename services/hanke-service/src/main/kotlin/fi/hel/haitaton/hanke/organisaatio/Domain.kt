package fi.hel.haitaton.hanke.organisaatio

import java.time.ZonedDateTime

data class Organisaatio(
    var id: Int? = null,
    var organisaatioTunnus: String? = null,
    var organisaatioNimi: String? = null,
    var createdAt: ZonedDateTime? = null,
    var updatedAt: ZonedDateTime? = null
)

class OrganisaatioNotFoundException() : RuntimeException("Cannot find organizations")
