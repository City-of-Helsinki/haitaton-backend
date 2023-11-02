package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.geometria.Geometriat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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
    override var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null,
    @field:Schema(
        description = "Street lane hindrance length",
    )
    override var kaistaPituusHaitta: KaistajarjestelynPituus? = null,
    @field:Schema(
        description = "Noise nuisance",
    )
    override var meluHaitta: Haitta13? = null,
    @field:Schema(
        description = "Dust nuisance",
    )
    override var polyHaitta: Haitta13? = null,
    @field:Schema(
        description = "Vibration nuisance",
    )
    override var tarinaHaitta: Haitta13? = null,
    @field:Schema(
        description = "Area name, must not be null or empty",
    )
    override var nimi: String? = null,
) : HasId<Int?>, Hankealue

fun List<Hankealue>.alkuPvm(): ZonedDateTime? = mapNotNull { it.haittaAlkuPvm }.minOfOrNull { it }

fun List<Hankealue>.loppuPvm(): ZonedDateTime? = mapNotNull { it.haittaLoppuPvm }.maxOfOrNull { it }

fun List<Hankealue>.kaistaHaitat(): Set<TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin> {
    return mapNotNull { it.kaistaHaitta }.toSet()
}

fun List<Hankealue>.kaistaPituusHaitat(): Set<KaistajarjestelynPituus> {
    return mapNotNull { it.kaistaPituusHaitta }.toSet()
}

fun List<SavedHankealue>.geometriat(): List<Geometriat> = mapNotNull { it.geometriat }

fun List<SavedHankealue>.geometriaIds(): Set<Int> = mapNotNull { it.geometriat?.id }.toSet()

fun List<Hankealue>.haittaAjanKestoDays(): Int? =
    if (alkuPvm() != null && loppuPvm() != null) {
        ChronoUnit.DAYS.between(alkuPvm(), loppuPvm()).toInt() + 1
    } else {
        null
    }
