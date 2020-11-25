package fi.hel.haitaton.hanke.domain


import fi.hel.haitaton.hanke.HankeYhteystietoEntity

import fi.hel.haitaton.hanke.Vaihe

import java.time.ZonedDateTime

/**
 * When creating Hanke, only creatorUserId is mandatory.
 * TODO: may be changing to a bit more of mandatory fields for at least draft saving.
 */
data class Hanke(

        var id: Int?, // Can be used for e.g. autosaving before hankeTunnus has been given (optional future stuff)

        var hankeTunnus: String?,
        var onYKTHanke: Boolean?,
        var nimi: String?,
        var kuvaus: String?,
        var alkuPvm: ZonedDateTime?,
        var loppuPvm: ZonedDateTime?,

        var vaihe: Vaihe?,


        var version: Int?,
        val createdBy: String,
        val createdAt: ZonedDateTime?,
        var modifiedBy: String?,
        var modifiedAt: ZonedDateTime?,

        // Default for machine API's. UI should always give the save type.

        var saveType: SaveType? = SaveType.SUBMIT) {

    var listOfOmistaja: List<HankeYhteystiedot> = arrayListOf()
    var listOfArvioija: List<HankeYhteystiedot> = arrayListOf()
    var listOfToteuttaja: List<HankeYhteystiedot> = arrayListOf()
}