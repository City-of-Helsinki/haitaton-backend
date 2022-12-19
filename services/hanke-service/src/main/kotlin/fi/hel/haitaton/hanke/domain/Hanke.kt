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
