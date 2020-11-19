package fi.hel.haitaton.hanke.domain

data class HankeYhteystiedot(
        var id: Long?,
        //e.g. omistaja, arvioija, toteuttaja
        var contactType: Int,

        //must have contact information
        var sukunimi: String,
        var etunimi: String,
        var email: String,
        var puhelinnumero: String,

        //organisaatio (optional)
        //   var virallinenOrganisaatio: VirallinenOrganisaatio?, //TODO, when we know about the official side
        var kaytSyotOrganisaationNimi: String?,
        var osasto: String?
)





