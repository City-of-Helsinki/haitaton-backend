package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

/** NOTE Remember to update PublicHankealue after changes */
@JsonView(ChangeLogView::class)
@Schema(description = "Area data of a Hanke")
data class SavedHankealue(
    @field:Schema(
        description = "Area identity, set by the service",
    )
    override var id: Int? = null,
    @field:Schema(
        description = "Hanke identity of this area",
    )
    var hankeId: Int? = null,
    @field:Schema(
        description = "Nuisance start date, must not be null",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    override var haittaAlkuPvm: ZonedDateTime? = null,
    @field:Schema(
        description = "Nuisance end date, must not be before haittaAlkuPvm",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    override var haittaLoppuPvm: ZonedDateTime? = null,
    @field:Schema(
        description = "Geometry data",
    )
    override var geometriat: Geometriat? = null,
    @field:Schema(
        description = "Street lane hindrance value and explanation",
    )
    override var kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
    @field:Schema(
        description = "Street lane hindrance length",
    )
    override var kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
    @field:Schema(
        description = "Noise nuisance",
    )
    override var meluHaitta: Meluhaitta? = null,
    @field:Schema(
        description = "Dust nuisance",
    )
    override var polyHaitta: Polyhaitta? = null,
    @field:Schema(
        description = "Vibration nuisance",
    )
    override var tarinaHaitta: Tarinahaitta? = null,
    @field:Schema(
        description = "Area name, must not be null or empty",
    )
    override var nimi: String,
    @field:Schema(
        description = "Collision review result for this area",
    )
    val tormaystarkasteluTulos: TormaystarkasteluTulos?,
    @field:Schema(
        description = "Nuisance control plan for this area",
    )
    override var haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma? = null,
) : HasId<Int?>, Hankealue

fun List<Hankealue>.alkuPvm(): ZonedDateTime? = mapNotNull { it.haittaAlkuPvm }.minOfOrNull { it }

fun List<Hankealue>.loppuPvm(): ZonedDateTime? = mapNotNull { it.haittaLoppuPvm }.maxOfOrNull { it }

fun List<SavedHankealue>.geometriat(): List<Geometriat> = mapNotNull { it.geometriat }
