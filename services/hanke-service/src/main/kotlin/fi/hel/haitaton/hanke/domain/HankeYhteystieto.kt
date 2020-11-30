package fi.hel.haitaton.hanke.domain

import java.time.ZonedDateTime

//e.g. omistaja, arvioija, toteuttaja
data class HankeYhteystieto(
        var id: Int?,

        //must have contact information fields:
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





