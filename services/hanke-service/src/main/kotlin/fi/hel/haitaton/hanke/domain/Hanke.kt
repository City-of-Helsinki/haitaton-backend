package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.NotInChangeLogView
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

@Schema(description = "The project within which applications are processed.")
data class Hanke(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Id, set by the service.")
    override val id: Int,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Hanke identity for external purposes, set by the service.",
        example = "HAI24-123",
    )
    override val hankeTunnus: String,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Shared Public Utility Site (Yhteinen kunnallistekninen työmaa). Optional."
    )
    var onYKTHanke: Boolean?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Name of the project, must not be blank.")
    var nimi: String,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description =
            "Description of the project and the work done during it. Required for the project to be published."
    )
    var kuvaus: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Current stage of the project. Required for the hanke to be published."
    )
    var vaihe: Hankevaihe?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Version, set by the service.")
    var version: Int?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "User id of the Hanke creator, set by the service.")
    val createdBy: String?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Timestamp of creation, set by the service.")
    val createdAt: ZonedDateTime?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "User id of the last modifier, set by the service.")
    var modifiedBy: String?,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Timestamp of last modification, set by the service.")
    var modifiedAt: ZonedDateTime?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Hanke current status, set by the service.")
    var status: HankeStatus? = HankeStatus.DRAFT,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Indicates if Hanke data is generated, set by the service.")
    var generated: Boolean = false,
) : HasYhteystiedot, HankeIdentifier {

    // --------------- Yhteystiedot -----------------
    @JsonView(NotInChangeLogView::class)
    @field:Schema(
        description =
            "Project owners, contact information. At least one is required for the hanke to be published."
    )
    override var omistajat = mutableListOf<HankeYhteystieto>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(
        description =
            "Property developers, contact information. Not required for the hanke to be published."
    )
    override var rakennuttajat = mutableListOf<HankeYhteystieto>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Executor of the work. Not required for the hanke to be published.")
    override var toteuttajat = mutableListOf<HankeYhteystieto>()

    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Other contacts. Not required for the hanke to be published.")
    override var muut = mutableListOf<HankeYhteystieto>()

    // --------------- Hankkeen lisätiedot / Työmaan tiedot -------------------
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Work site street address. Required for the hanke to be published.",
        maxLength = 2000,
    )
    var tyomaaKatuosoite: String? = null

    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Work site types. Not required for the hanke to be published.")
    var tyomaaTyyppi = mutableSetOf<TyomaaTyyppi>()

    val alkuPvm: ZonedDateTime?
        @JsonView(ChangeLogView::class) get(): ZonedDateTime? = alueet.alkuPvm()

    val loppuPvm: ZonedDateTime?
        @JsonView(ChangeLogView::class) get(): ZonedDateTime? = alueet.loppuPvm()

    @JsonView(ChangeLogView::class)
    @field:Schema(
        description =
            "Hanke areas data. At least one alue is required for the hanke to be published."
    )
    var alueet = mutableListOf<SavedHankealue>()

    override fun extractYhteystiedot(): List<HankeYhteystieto> =
        listOfNotNull(omistajat, rakennuttajat, toteuttajat, muut).flatten()
}

enum class HankeStatus {
    /** A hanke is a draft from its creation until all mandatory fields have been filled. */
    DRAFT,

    /**
     * A hanke goes public after all mandatory fields have been filled. This happens automatically
     * on any update. A public hanke has some info visible to everyone and applications can be added
     * to it.
     */
    PUBLIC,

    /**
     * After the end dates of all hankealue have passed, a hanke is considered finished. It's
     * anonymized and at least mostly hidden in the UI.
     */
    ENDED,
}

enum class Hankevaihe {
    OHJELMOINTI,
    SUUNNITTELU,
    RAKENTAMINEN,
}

enum class TyomaaTyyppi {
    VESI,
    VIEMARI,
    SADEVESI,
    SAHKO,
    TIETOLIIKENNE,
    LIIKENNEVALO,
    ULKOVALAISTUS,
    KAAPPITYO,
    KAUKOLAMPO,
    KAUKOKYLMA,
    KAASUJOHTO,
    KISKOTYO,
    MUU,
    KADUNRAKENNUS,
    KADUN_KUNNOSSAPITO,
    KIINTEISTOLIITTYMA,
    SULKU_TAI_KAIVO,
    UUDISRAKENNUS,
    SANEERAUS,
    AKILLINEN_VIKAKORJAUS,
    VIHERTYO,
    RUNKOLINJA,
    NOSTOTYO,
    MUUTTO,
    PYSAKKITYO,
    KIINTEISTOREMONTTI,
    ULKOMAINOS,
    KUVAUKSET,
    LUMENPUDOTUS,
    YLEISOTILAISUUS,
    VAIHTOLAVA,
}
