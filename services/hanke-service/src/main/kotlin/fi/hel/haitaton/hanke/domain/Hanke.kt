package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TyomaaKoko
import fi.hel.haitaton.hanke.TyomaaTyyppi
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
        var suunnitteluVaihe: SuunnitteluVaihe?,

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

    var tyomaaKatuosoite: String? = null
    var tyomaaTyyppi: MutableSet<TyomaaTyyppi> = mutableSetOf()
    var tyomaaKoko: TyomaaKoko? = null
}