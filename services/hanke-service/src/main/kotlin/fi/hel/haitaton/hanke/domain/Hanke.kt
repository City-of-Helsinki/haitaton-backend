package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.TyomaaKoko
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaIndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// TODO: should give most constructor parameters a default value (or move out of constructor),
// and instead ensure that there are explicit checks for the mandatory fields in the validator.
// Current way causes a lot of bloat in test methods, yet gives no real benefit.
// The original thinking was that the constructor has the first page fields, which are
// mandatory... but e.g. audit fields are internal stuff, should be outside of constructor.
// The added constructors help a bit with the above, but have limited options.
data class Hanke(
    /**
     * Can be used for e.g. autosaving before hankeTunnus has been given (optional future stuff).
     */
    var id: Int?,
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

    /** Default for machine API's. UI should always give the save type. */
    var saveType: SaveType? = SaveType.SUBMIT
) {

    constructor(
        id: Int
    ) : this(id, null, null, null, null, null, null, null, null, null, null, null, null, null)
    constructor(
        id: Int,
        hankeTunnus: String
    ) : this(
        id,
        hankeTunnus,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    )
    constructor() :
        this(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
    constructor(
        nimi: String,
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
    fun kaistaHaitat(): Set<TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin> {
        return alueet.map { it.kaistaHaitta }.filterNotNull().toSet()
    }

    fun kaistaPituusHaitat(): Set<KaistajarjestelynPituus> {
        return alueet.map { it.kaistaPituusHaitta }.filterNotNull().toSet()
    }

    fun meluHaitat(): Set<Haitta13> {
        return alueet.map { it.meluHaitta }.filterNotNull().toSet()
    }

    fun polyHaitat(): Set<Haitta13> {
        return alueet.map { it.polyHaitta }.filterNotNull().toSet()
    }

    fun tarinaHaitat(): Set<Haitta13> {
        return alueet.map { it.tarinaHaitta }.filterNotNull().toSet()
    }

    var alueet = mutableListOf<Hankealue>()

    var permissions: List<PermissionCode>? = null

    /** Number of days between haittaAlkuPvm and haittaLoppuPvm (incl. both days) */
    val haittaAjanKestoDays: Int?
        @JsonIgnore
        get() =
            if (getHaittaAlkuPvm() != null && getHaittaLoppuPvm() != null) {
                ChronoUnit.DAYS.between(getHaittaAlkuPvm()!!, getHaittaLoppuPvm()!!).toInt() + 1
            } else {
                null
            }

    val liikennehaittaindeksi: LiikennehaittaIndeksiType? by lazy {
        tormaystarkasteluTulos?.liikennehaittaIndeksi
    }

    var tormaystarkasteluTulos: TormaystarkasteluTulos? = null

    fun getHaittaAlkuPvm(): ZonedDateTime? {
        return alueet.map { it.haittaAlkuPvm }.filterNotNull().minOfOrNull { it }
    }

    fun getHaittaLoppuPvm(): ZonedDateTime? {
        return alueet.map { it.haittaLoppuPvm }.filterNotNull().maxOfOrNull { it }
    }

    fun onKaikkiPakollisetLuontiTiedot() =
        !nimi.isNullOrBlank() &&
            !kuvaus.isNullOrBlank() &&
            (alkuPvm != null) &&
            (loppuPvm != null) &&
            hasMandatoryVaiheValues() &&
            saveType == SaveType.SUBMIT

    private fun hasMandatoryVaiheValues(): Boolean {
        // Vaihe must be given, but suunnitteluVaihe is mandatory only if vaihe is "SUUNNITTELU".
        if (vaihe == null) return false
        if (vaihe == Vaihe.SUUNNITTELU && suunnitteluVaihe == null) return false
        return true
    }

    fun toLogString(): String {
        return toString()
    }

    fun alueidenGeometriat(): List<Geometriat> {
        return this.alueet.map { it.geometriat }.filterNotNull()
    }
}
