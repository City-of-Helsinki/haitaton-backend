package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.geometria.Geometriat
import java.time.ZonedDateTime

/**
 *
 * NOTE Remember to update PublicHankealue after changes
 */
data class Hankealue(
    @JsonView(ChangeLogView::class) override var id: Int? = null,
    @JsonView(ChangeLogView::class) var hankeId: Int? = null,
    @JsonView(ChangeLogView::class) var haittaAlkuPvm: ZonedDateTime? = null,
    @JsonView(ChangeLogView::class) var haittaLoppuPvm: ZonedDateTime? = null,
    var geometriat: Geometriat? = null,
    @JsonView(ChangeLogView::class)
    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null,
    @JsonView(ChangeLogView::class) var kaistaPituusHaitta: KaistajarjestelynPituus? = null,
    @JsonView(ChangeLogView::class) var meluHaitta: Haitta13? = null,
    @JsonView(ChangeLogView::class) var polyHaitta: Haitta13? = null,
    @JsonView(ChangeLogView::class) var tarinaHaitta: Haitta13? = null,
) : HasId<Int>
