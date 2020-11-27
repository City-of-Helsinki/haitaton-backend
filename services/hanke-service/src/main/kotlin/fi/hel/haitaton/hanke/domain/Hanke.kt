package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.Haitta04
import fi.hel.haitaton.hanke.Haitta13
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

    var omistajat: MutableList<HankeYhteystieto> = arrayListOf()
    var arvioijat: MutableList<HankeYhteystieto> = arrayListOf()
    var toteuttajat: MutableList<HankeYhteystieto> = arrayListOf()

    var tyomaaKatuosoite: String? = null
    var tyomaaTyyppi: MutableSet<TyomaaTyyppi> = mutableSetOf()
    var tyomaaKoko: TyomaaKoko? = null

    var haittaAlkuPvm: ZonedDateTime? = null
    var haittaLoppuPvm: ZonedDateTime? = null
    var kaistaHaitta: Haitta04? = null
    var kaistaPituusHaitta: Haitta04? = null
    var meluHaitta: Haitta13? = null
    var polyHaitta: Haitta13? = null
    var tarinaHaitta: Haitta13? = null

}