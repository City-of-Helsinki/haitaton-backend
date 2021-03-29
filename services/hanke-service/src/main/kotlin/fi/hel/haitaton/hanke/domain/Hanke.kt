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
// The added constructors help a bit with the above, but have limited options.
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
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
    constructor(        nimi: String,
        alkuPvm: ZonedDateTime?,
        loppuPvm: ZonedDateTime?,
        vaihe: Vaihe,
        saveType: SaveType
    ) : this(
        null,
        null,
        null,
        nimi,
        null,
        alkuPvm,
        loppuPvm,
        vaihe,
        null,
        null,
        null,
        null,
        null,
        null,
        saveType
    )

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
    val haittaAjanKestoDays: Long?
        @JsonIgnore
        get() = if (haittaAlkuPvm != null && haittaLoppuPvm != null) {
            ChronoUnit.DAYS.between(haittaAlkuPvm!!, haittaLoppuPvm!!) + 1
        } else {
            null
        }

    // -------------- Tormaystarkastelu -------------
    /**
     * Liikennehaittaindeksi is always available here if it has been calculated and is still valid.
     * Might be around even if no longer valid.
     * TODO: should the validity-state be part of this indeksitype, too? (I.e. would know that even
     *  when not getting the full tulos-data (which does have that state).
     */
    var liikennehaittaindeksi: LiikennehaittaIndeksiType? = null

    /**
     * These are currently available only when Hanke is returned via Tormaystarkastelu controller/service.
     * And if they have been calculated and are still valid.
     */
    var tormaystarkasteluTulos: TormaystarkasteluTulos? = null

    // --------------- State flags -------------------
    var tilat: HankeTilat = HankeTilat()

    fun updateStateFlags() {
        updateStateFlagOnKaikkiPakollisetLuontiTiedot()
        updateStateFlagOnTiedotLiikHaittaIndeksille()
        updateStateFlagOnLiikenneHaittaIndeksi()
    }

    fun updateStateFlagOnKaikkiPakollisetLuontiTiedot() {
        // All mandatory fields have been given... (though their validity should be checked elsewhere)
        //  and saveType is submit, not just draft?
        tilat.onKaikkiPakollisetLuontiTiedot = !nimi.isNullOrBlank()
                && !kuvaus.isNullOrBlank()
                && (alkuPvm != null) && (loppuPvm != null)
                && hasMandatoryVaiheValues()
                && (kaistaHaitta != null) && (kaistaPituusHaitta != null)
                && tilat.onGeometrioita == true
                && saveType == SaveType.SUBMIT
    }

    fun updateStateFlagOnTiedotLiikHaittaIndeksille() {
        // Requires start date, stop date, geometry, and both kaista-related haittas.
        // (They don't have to be "valid", though, that is another thing.)
        tilat.onTiedotLiikenneHaittaIndeksille = (alkuPvm != null) && (loppuPvm != null)
                && (kaistaHaitta != null) && (kaistaPituusHaitta != null)
                && tilat.onGeometrioita == true
    }

    /**
     * NOTE: does not consider whether the result has been invalidated or not,
     * only that one exists.
     */
    fun updateStateFlagOnLiikenneHaittaIndeksi() {
        tilat.onLiikenneHaittaIndeksi = (liikennehaittaindeksi != null)
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
