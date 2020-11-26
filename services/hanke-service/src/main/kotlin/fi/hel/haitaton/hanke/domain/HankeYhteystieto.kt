package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.ContactType
import java.time.ZonedDateTime

data class HankeYhteystieto(
        var id: Int?,
        //e.g. omistaja, arvioija, toteuttaja
        var contactType: ContactType,  //TODO: don't bring to front, decide from list

        //must have contact information
        var sukunimi: String,
        var etunimi: String,
        var email: String,
        var puhelinnumero: String,

        //organisaatio (optional)
        var organisaatioId: Int?,
        var organisaatioNimi: String?,
        var osasto: String?,

        var createdBy: String?,
        var createdAt: ZonedDateTime?,
        var modifiedBy: String?,
        var modifiedAt: ZonedDateTime?
)





