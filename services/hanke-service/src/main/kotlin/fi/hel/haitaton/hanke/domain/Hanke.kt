package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.Haitta04
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TyomaaKoko
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.geometria.HankeGeometriat

import java.time.ZonedDateTime

/**
 *
 */
// TODO: should give most constructor parameters a default value (or move out of constructor),
// and instead ensure that there are explicit checks for the mandatory fields in the validator.
// Current way causes a lot of bloat in test methods, yet gives no real benefit.
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
        val createdBy: String?,
        val createdAt: ZonedDateTime?,
        var modifiedBy: String?,
        var modifiedAt: ZonedDateTime?,

        // Default for machine API's. UI should always give the save type.
        var saveType: SaveType? = SaveType.SUBMIT) {

    constructor(id: Int) : this(id, null, null, null, null, null, null, null, null, null, null, null, null, null)
    constructor(id: Int, hankeTunnus: String) : this(id, hankeTunnus, null, null, null, null, null, null, null, null, null, null, null, null)

    // --------------- Yhteystiedot -----------------
    var omistajat = mutableListOf<HankeYhteystieto>()
    var arvioijat = mutableListOf<HankeYhteystieto>()
    var toteuttajat = mutableListOf<HankeYhteystieto>()

    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    var tyomaaKatuosoite: String? = null
    var tyomaaTyyppi = mutableSetOf<TyomaaTyyppi>()
    var tyomaaKoko: TyomaaKoko? = null

    // --------------- Hankkeen haitat -------------------
    var haittaAlkuPvm: ZonedDateTime? = null
    var haittaLoppuPvm: ZonedDateTime? = null
    var kaistaHaitta: Haitta04? = null
    var kaistaPituusHaitta: Haitta04? = null
    var meluHaitta: Haitta13? = null
    var polyHaitta: Haitta13? = null
    var tarinaHaitta: Haitta13? = null

    /** Note: this can be null for two reasons; the field wasn't requested for, or there are no geometries for the hanke.
     * See 'tilaOnGeometrioita' field.
     */
    var geometriat: HankeGeometriat? = null

    // --------------- State flags -------------------
    // TODO: englanniksi?
    var tilaOnGeometrioita: Boolean? = false
    var tilaOnKaikkiPakollisetLuontiTiedot: Boolean? = false
    var tilaOnTiedotLiikHaittaIndeksille: Boolean? = false
    var tilaOnLiikHaittaIndeksi: Boolean? = false
    var tilaOnViereisiaHankkeita: Boolean? = false
    var tilaOnAsiakasryhmia: Boolean? = false

    fun updateStateFlagOnKaikkiPakollisetLuontiTiedot() {
        // TODO: all mandatory fields have been given... (though their validity should be checked elsewhere)
        //  and saveType is submit, not just draft?
        // For now, a dummy solution giving pseudo-random value derived from the fields:
        var i = (id?.hashCode() ?: 0) + (hankeTunnus?.hashCode() ?: 0) + (nimi?.hashCode() ?: 0) + (createdAt?.hashCode() ?: 0)
        tilaOnKaikkiPakollisetLuontiTiedot = (i % 2 == 0)
    }
    fun updateStateFlagTiedotLiikHaittaIndeksille() {
        // Requires start date, stop date and geometry. (They don't have to be "valid", though, that is another thing.)
        tilaOnTiedotLiikHaittaIndeksille = (alkuPvm != null) && (loppuPvm != null) && tilaOnGeometrioita == true
    }

}