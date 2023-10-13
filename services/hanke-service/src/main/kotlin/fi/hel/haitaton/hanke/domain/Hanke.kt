package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaIndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

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
    override var nimi: String,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Description of the Hanke contents ")
    var kuvaus: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke current stage", required = true)
    override var vaihe: Vaihe?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Hanke current planning stage, must be defined if vaihe = SUUNNITTELU"
    )
    override var suunnitteluVaihe: SuunnitteluVaihe?,
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
    @field:Schema(description = "Indicates whether the Hanke data is generated, set by the service")
    var generated: Boolean = false,
) : HasId<Int>, BaseHanke {

    // --------------- Yhteystiedot -----------------
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Project owners, contact information")
    override var omistajat = mutableListOf<HankeYhteystieto>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Property developers, contact information")
    override var rakennuttajat = mutableListOf<HankeYhteystieto>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Executor of the work")
    override var toteuttajat = mutableListOf<HankeYhteystieto>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Other contacts")
    override var muut = mutableListOf<HankeYhteystieto>()

    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Work site street address", maxLength = 2000)
    override var tyomaaKatuosoite: String? = null

    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Work site types")
    var tyomaaTyyppi = mutableSetOf<TyomaaTyyppi>()

    val alkuPvm: ZonedDateTime?
        @JsonView(ChangeLogView::class) get(): ZonedDateTime? = alueet.alkuPvm()

    val loppuPvm: ZonedDateTime?
        @JsonView(ChangeLogView::class) get(): ZonedDateTime? = alueet.loppuPvm()

    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke areas data")
    override var alueet = mutableListOf<Hankealue>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Permission codes to this Hanke project")
    var permissions: List<PermissionCode>? = null

    /** Number of days between haittaAlkuPvm and haittaLoppuPvm (incl. both days) */
    val haittaAjanKestoDays: Int?
        @JsonIgnore get() = alueet.haittaAjanKestoDays()

    @JsonView(NotInChangeLogView::class)
    fun getLiikennehaittaindeksi(): LiikennehaittaIndeksiType? =
        tormaystarkasteluTulos?.liikennehaittaIndeksi

    @JsonView(ChangeLogView::class)
    @field:Schema(description = "")
    var tormaystarkasteluTulos: TormaystarkasteluTulos? = null

    fun toLogString(): String {
        return toString()
    }
}
