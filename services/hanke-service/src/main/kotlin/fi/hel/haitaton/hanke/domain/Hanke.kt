package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaIndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class Hanke(
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
    @JsonView(NotInChangeLogView::class) val createdBy: String?,
    @JsonView(NotInChangeLogView::class) val createdAt: ZonedDateTime?,
    @JsonView(NotInChangeLogView::class) var modifiedBy: String?,
    @JsonView(NotInChangeLogView::class) var modifiedAt: ZonedDateTime?,
    @JsonView(ChangeLogView::class) var status: HankeStatus? = HankeStatus.DRAFT,
    @JsonView(ChangeLogView::class) var perustaja: Perustaja? = null,
    @JsonView(ChangeLogView::class) var generated: Boolean? = false,
) : HasId<Int> {

    // --------------- Yhteystiedot -----------------
    @JsonView(NotInChangeLogView::class) var omistajat = mutableListOf<HankeYhteystieto>()
    @JsonView(NotInChangeLogView::class) var arvioijat = mutableListOf<HankeYhteystieto>()
    @JsonView(NotInChangeLogView::class) var toteuttajat = mutableListOf<HankeYhteystieto>()

    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    @JsonView(ChangeLogView::class) var tyomaaKatuosoite: String? = null
    @JsonView(ChangeLogView::class) var tyomaaTyyppi = mutableSetOf<TyomaaTyyppi>()

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

    @JsonView(ChangeLogView::class) var alueet = mutableListOf<Hankealue>()

    @JsonView(NotInChangeLogView::class) var permissions: List<PermissionCode>? = null

    /** Number of days between haittaAlkuPvm and haittaLoppuPvm (incl. both days) */
    val haittaAjanKestoDays: Int?
        @JsonIgnore
        get() =
            if (getHaittaAlkuPvm() != null && getHaittaLoppuPvm() != null) {
                ChronoUnit.DAYS.between(getHaittaAlkuPvm()!!, getHaittaLoppuPvm()!!).toInt() + 1
            } else {
                null
            }

    @JsonView(NotInChangeLogView::class)
    fun getLiikennehaittaindeksi(): LiikennehaittaIndeksiType? =
        tormaystarkasteluTulos?.liikennehaittaIndeksi

    @JsonView(ChangeLogView::class) var tormaystarkasteluTulos: TormaystarkasteluTulos? = null

    @JsonView(NotInChangeLogView::class)
    fun getHaittaAlkuPvm(): ZonedDateTime? {
        return alueet.map { it.haittaAlkuPvm }.filterNotNull().minOfOrNull { it }
    }

    @JsonView(NotInChangeLogView::class)
    fun getHaittaLoppuPvm(): ZonedDateTime? {
        return alueet.map { it.haittaLoppuPvm }.filterNotNull().maxOfOrNull { it }
    }

    fun toLogString(): String {
        return toString()
    }

    fun alueidenGeometriat(): List<Geometriat> {
        return this.alueet.map { it.geometriat }.filterNotNull()
    }
}
