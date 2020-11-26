package fi.hel.haitaton.hanke.organisaatio

data class Organisaatio(
    var id: Int? = null,
    var tunnus: String? = null,
    var nimi: String? = null
)

class OrganisaatioNotFoundException() : RuntimeException("Cannot find organizations")
