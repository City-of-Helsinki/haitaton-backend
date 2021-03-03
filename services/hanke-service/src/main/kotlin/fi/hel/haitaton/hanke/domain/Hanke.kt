package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.TyomaaKoko
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaIndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 *
 */
// TODO: should give most constructor parameters a default value (or move out of constructor),
// and instead ensure that there are explicit checks for the mandatory fields in the validator.
// Current way causes a lot of bloat in test methods, yet gives no real benefit.
// The original thinking was that the constructor has the first page fields, which are
// mandatory... but e.g. audit fields are internal stuff, should be outside of constructor.
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

    // -------------- Tormaystarkastelu -------------
    var tormaystarkasteluTulos: TormaystarkasteluTulos? = null
    var liikennehaittaindeksi: LiikennehaittaIndeksiType? = null

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
    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null
    var kaistaPituusHaitta: KaistajarjestelynPituus? = null
    var meluHaitta: Haitta13? = null
    var polyHaitta: Haitta13? = null
    var tarinaHaitta: Haitta13? = null

    /** Note: this can be null for two reasons; the field wasn't requested for, or there are no geometries for the hanke.
     * See 'tilaOnGeometrioita' field.
     */
    var geometriat: HankeGeometriat? = null

    /**
     * Number of days between haittaAlkuPvm and haittaLoppuPvm (incl. both days)
     */
    val haittaAjanKesto: Long?
        @JsonIgnore
        get() = if (haittaAlkuPvm != null && haittaLoppuPvm != null) {
            ChronoUnit.DAYS.between(haittaAlkuPvm!!, haittaLoppuPvm!!) + 1
        } else {
            null
        }

    // --------------- State flags -------------------
    var tilat: HankeTilat = HankeTilat()

    fun updateStateFlags() {
        updateStateFlagOnKaikkiPakollisetLuontiTiedot()
        updateStateFlagTiedotLiikHaittaIndeksille()
    }

    fun updateStateFlagOnKaikkiPakollisetLuontiTiedot() {
        // All mandatory fields have been given... (though their validity should be checked elsewhere)
        //  and saveType is submit, not just draft?
        tilat.onKaikkiPakollisetLuontiTiedot = !nimi.isNullOrBlank()
                && !kuvaus.isNullOrBlank()
                && (alkuPvm != null) && (loppuPvm != null)
                && hasMandatoryVaiheValues()
                // TODO: Not certain if this is required; remove/uncomment later once it gets decided
                // && !tyomaaKatuosoite.isNullOrBlank()
                && (kaistaHaitta != null) && (kaistaPituusHaitta != null)
                && tilat.onGeometrioita == true
                && saveType == SaveType.SUBMIT
    }

    fun updateStateFlagTiedotLiikHaittaIndeksille() {
        // Requires start date, stop date, geometry, and both kaista-related haittas.
        // (They don't have to be "valid", though, that is another thing.)
        tilat.onTiedotLiikenneHaittaIndeksille = (alkuPvm != null) && (loppuPvm != null)
                && (kaistaHaitta != null) && (kaistaPituusHaitta != null)
                && tilat.onGeometrioita == true
    }

    private fun hasMandatoryVaiheValues(): Boolean {
        // Vaihe must be given, but suunnitteluVaihe is mandatory only if vaihe is "SUUNNITTELU".
        if (vaihe == null) return false
        if (vaihe == Vaihe.SUUNNITTELU && suunnitteluVaihe == null) return false
        return true
    }

    fun toLogString(): String {
        return toString()
    }
}
