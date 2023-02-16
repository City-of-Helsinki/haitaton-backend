package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.geometria.Geometriat
import java.time.ZonedDateTime

/** NOTE Remember to update PublicHankealue after changes */
@JsonView(ChangeLogView::class)
data class Hankealue(
    override var id: Int? = null,
    var hankeId: Int? = null,
    var haittaAlkuPvm: ZonedDateTime? = null,
    var haittaLoppuPvm: ZonedDateTime? = null,
    var geometriat: Geometriat? = null,
    var kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null,
    var kaistaPituusHaitta: KaistajarjestelynPituus? = null,
    var meluHaitta: Haitta13? = null,
    var polyHaitta: Haitta13? = null,
    var tarinaHaitta: Haitta13? = null,
    var nimi: String? = null
) : HasId<Int>
