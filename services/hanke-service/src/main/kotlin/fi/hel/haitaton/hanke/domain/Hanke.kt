package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.TyomaaKoko
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
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

    /** Can be used for e.g. autosaving before hankeTunnus has been given (optional future stuff) */
    @JsonView(ChangeLogView::class) override var id: Int?,
    @JsonView(ChangeLogView::class) var hankeTunnus: String?,
    @JsonView(ChangeLogView::class) var onYKTHanke: Boolean?,
    @JsonView(ChangeLogView::class) var nimi: String?,
    @JsonView(ChangeLogView::class) var kuvaus: String?,
    @JsonView(ChangeLogView::class) var alkuPvm: ZonedDateTime?,
    @JsonView(ChangeLogView::class) var loppuPvm: ZonedDateTime?,
    @JsonView(ChangeLogView::class) var vaihe: Vaihe?,
    @JsonView(ChangeLogView::class) var suunnitteluVaihe: SuunnitteluVaihe?,
    @JsonView(ChangeLogView::class) var version: Int?,
    val createdBy: String?,
    val createdAt: ZonedDateTime?,
    var modifiedBy: String?,
    var modifiedAt: ZonedDateTime?,

    // Default for machine API's. UI should always give the save type.
    var saveType: SaveType? = SaveType.SUBMIT
) : HasId<Int> {

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
    @JsonView(ChangeLogView::class) var tyomaaKatuosoite: String? = null
    @JsonView(ChangeLogView::class) var tyomaaTyyppi = mutableSetOf<TyomaaTyyppi>()
    @JsonView(ChangeLogView::class) var tyomaaKoko: TyomaaKoko? = null

    // --------------- Hankkeen haitat -------------------
    @JsonView(ChangeLogView::class) var haittaAlkuPvm: ZonedDateTime? = null
    @JsonView(ChangeLogView::class) var haittaLoppuPvm: ZonedDateTime? = null
    @JsonView(ChangeLogView::class)
    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null
    @JsonView(ChangeLogView::class) var kaistaPituusHaitta: KaistajarjestelynPituus? = null
    @JsonView(ChangeLogView::class) var meluHaitta: Haitta13? = null
    @JsonView(ChangeLogView::class) var polyHaitta: Haitta13? = null
    @JsonView(ChangeLogView::class) var tarinaHaitta: Haitta13? = null

    /**
     * Note: this can be null for two reasons; the field wasn't requested for, or there are no
     * geometries for the hanke.
     */
    var geometriat: HankeGeometriat? = null

    var permissions: List<PermissionCode>? = null

    /** Number of days between haittaAlkuPvm and haittaLoppuPvm (incl. both days) */
    val haittaAjanKestoDays: Int?
        @JsonIgnore
        get() =
            if (haittaAlkuPvm != null && haittaLoppuPvm != null) {
                ChronoUnit.DAYS.between(haittaAlkuPvm!!, haittaLoppuPvm!!).toInt() + 1
            } else {
                null
            }

    val liikennehaittaindeksi: LiikennehaittaIndeksiType? by lazy {
        tormaystarkasteluTulos?.liikennehaittaIndeksi
    }

    @JsonView(ChangeLogView::class) var tormaystarkasteluTulos: TormaystarkasteluTulos? = null

    fun onKaikkiPakollisetLuontiTiedot() =
        !nimi.isNullOrBlank() &&
            !kuvaus.isNullOrBlank() &&
            (alkuPvm != null) &&
            (loppuPvm != null) &&
            hasMandatoryVaiheValues() &&
            (kaistaHaitta != null) &&
            (kaistaPituusHaitta != null) &&
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
}
