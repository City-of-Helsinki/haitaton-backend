package fi.hel.haitaton.hanke.organisaatio

interface OrganisaatioService {
    fun getOrganisaatiot(): Iterable<Organisaatio>
}
