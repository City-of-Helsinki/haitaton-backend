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
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Schema(description = "The project within which applications are processed")
data class Hanke(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Id, set by the service")
    override var id: Int?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Hanke identity for external purposes, set by the service",
        example = "HAI24-123"
    )
    var hankeTunnus: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Yhteinen kunnallistekninen työmaa")
    var onYKTHanke: Boolean?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Name of the Hanke, must not be blank")
    var nimi: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Description of the Hanke contents ")
    var kuvaus: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke current stage", required = true)
    var vaihe: Vaihe?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Hanke current planning stage, must be defined if vaihe = SUUNNITTELU"
    )
    var suunnitteluVaihe: SuunnitteluVaihe?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Version, set by the service")
    var version: Int?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "User id of the Hanke creator, set by the service")
    val createdBy: String?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Timestamp of creation, set by the service")
    val createdAt: ZonedDateTime?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "User id of the last modifier, set by the service")
    var modifiedBy: String?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Timestamp of last modification, set by the service")
    var modifiedAt: ZonedDateTime?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke current status, set by the service")
    var status: HankeStatus? = HankeStatus.DRAFT,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke founder contact information")
    var perustaja: Perustaja? = null,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Indicates whether the Hanke data is generated, set by the service")
    var generated: Boolean = false,
) : HasId<Int> {

    // --------------- Yhteystiedot -----------------
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Project owners, contact information")
    var omistajat = mutableListOf<HankeYhteystieto>()
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Property developers, contact information")
    var rakennuttajat = mutableListOf<HankeYhteystieto>()
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Executor of the work")
    var toteuttajat = mutableListOf<HankeYhteystieto>()
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Other contacts")
    var muut = mutableListOf<HankeYhteystieto>()

    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Work site street address", maxLength = 2000)
    var tyomaaKatuosoite: String? = null
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Work site types")
    var tyomaaTyyppi = mutableSetOf<TyomaaTyyppi>()

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

    val alkuPvm: ZonedDateTime?
        @JsonView(ChangeLogView::class)
        get(): ZonedDateTime? = alueet.mapNotNull { it.haittaAlkuPvm }.minOfOrNull { it }

    val loppuPvm: ZonedDateTime?
        @JsonView(ChangeLogView::class)
        get(): ZonedDateTime? = alueet.mapNotNull { it.haittaLoppuPvm }.maxOfOrNull { it }

    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke areas data")
    var alueet = mutableListOf<Hankealue>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Permission codes to this Hanke project")
    var permissions: List<PermissionCode>? = null

    /** Number of days between haittaAlkuPvm and haittaLoppuPvm (incl. both days) */
    val haittaAjanKestoDays: Int?
        @JsonIgnore
        get() =
            if (alkuPvm != null && loppuPvm != null) {
                ChronoUnit.DAYS.between(alkuPvm!!, loppuPvm!!).toInt() + 1
            } else {
                null
            }

    @JsonView(NotInChangeLogView::class)
    fun getLiikennehaittaindeksi(): LiikennehaittaIndeksiType? =
        tormaystarkasteluTulos?.liikennehaittaIndeksi

    @JsonView(ChangeLogView::class)
    @field:Schema(description = "")
    var tormaystarkasteluTulos: TormaystarkasteluTulos? = null

    fun toLogString(): String {
        return toString()
    }

    fun alueidenGeometriat(): List<Geometriat> {
        return this.alueet.map { it.geometriat }.filterNotNull()
    }

    fun extractYhteystiedot(): List<HankeYhteystieto> =
        omistajat + rakennuttajat + toteuttajat + muut
}
