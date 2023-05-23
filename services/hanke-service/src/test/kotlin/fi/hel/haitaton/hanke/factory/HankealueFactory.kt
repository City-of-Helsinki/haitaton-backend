package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import java.time.ZonedDateTime

object HankealueFactory {

    fun create(
        id: Int? = 1,
        hankeId: Int? = 2,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(),
        kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? =
            TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI,
        kaistaPituusHaitta: KaistajarjestelynPituus? = KaistajarjestelynPituus.NELJA,
        meluHaitta: Haitta13? = Haitta13.YKSI,
        polyHaitta: Haitta13? = Haitta13.KAKSI,
        tarinaHaitta: Haitta13? = Haitta13.KOLME,
        nimi: String? = null,
    ): Hankealue {
        return Hankealue(
            id,
            hankeId,
            haittaAlkuPvm,
            haittaLoppuPvm,
            geometriat,
            kaistaHaitta,
            kaistaPituusHaitta,
            meluHaitta,
            polyHaitta,
            tarinaHaitta,
            nimi,
        )
    }

    fun createMinimal(
        id: Int? = null,
        hankeId: Int? = null,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = null,
        kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? = null,
        kaistaPituusHaitta: KaistajarjestelynPituus? = null,
        meluHaitta: Haitta13? = null,
        polyHaitta: Haitta13? = null,
        tarinaHaitta: Haitta13? = null,
        nimi: String? = null,
    ): Hankealue {
        return Hankealue(
            id,
            hankeId,
            haittaAlkuPvm,
            haittaLoppuPvm,
            geometriat,
            kaistaHaitta,
            kaistaPituusHaitta,
            meluHaitta,
            polyHaitta,
            tarinaHaitta,
            nimi,
        )
    }
}
