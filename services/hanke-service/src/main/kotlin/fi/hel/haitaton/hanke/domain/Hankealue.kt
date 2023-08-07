package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.geometria.Geometriat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

/** NOTE Remember to update PublicHankealue after changes */
@JsonView(ChangeLogView::class)
@Schema(description = "Area data of a Hanke")
data class Hankealue(
    @field:Schema(description = "Area identity, set by the service") override var id: Int? = null,
    //
    @field:Schema(description = "Hanke identity of this area") var hankeId: Int? = null,
    //
    @field:Schema(
        description = "Nuisance start date, must not be null",
        maximum = "2099-12-31T23:59:59.99Z"
    )
    var haittaAlkuPvm: ZonedDateTime? = null,
    //
    @field:Schema(
        description = "Nuisance end date, must not be before haittaAlkuPvm",
        maximum = "2099-12-31T23:59:59.99Z"
    )
    var haittaLoppuPvm: ZonedDateTime? = null,
    //
    @field:Schema(description = "Geometry data") var geometriat: Geometriat? = null,
    //
    @field:Schema(description = "Street lane nuisance value and explanation")
    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null,
    //
    @field:Schema(description = "Street lane nuisance length")
    var kaistaPituusHaitta: KaistajarjestelynPituus? = null,
    //
    @field:Schema(description = "Noise nuisance") var meluHaitta: Haitta13? = null,
    //
    @field:Schema(description = "Dust nuisance") var polyHaitta: Haitta13? = null,
    //
    @field:Schema(description = "Vibration nuisance") var tarinaHaitta: Haitta13? = null,
    //
    @field:Schema(description = "Area name") var nimi: String? = null,
) : HasId<Int>
