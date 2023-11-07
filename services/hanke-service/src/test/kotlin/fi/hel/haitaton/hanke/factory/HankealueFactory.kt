package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import java.time.ZonedDateTime

object HankealueFactory {

    fun create(
        id: Int? = 1,
        hankeId: Int? = 2,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = GeometriaFactory.create(),
        kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin? =
            TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI,
        kaistaPituusHaitta: KaistajarjestelynPituus? = KaistajarjestelynPituus.NELJA,
        meluHaitta: Haitta13? = Haitta13.YKSI,
        polyHaitta: Haitta13? = Haitta13.KAKSI,
        tarinaHaitta: Haitta13? = Haitta13.KOLME,
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
    ): SavedHankealue {
        return SavedHankealue(
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
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
    ): SavedHankealue {
        return SavedHankealue(
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

    fun createHankeAlueEntity(mockId: Int = 1, hankeEntity: HankeEntity): HankealueEntity {
        val alue = create(id = mockId).apply { geometriat?.id = mockId }
        return HankealueEntity().apply {
            id = alue.id!!
            hanke = hankeEntity
            geometriat = alue.geometriat?.id
            haittaAlkuPvm = DateFactory.getStartDatetime().toLocalDate()
            haittaLoppuPvm = DateFactory.getEndDatetime().toLocalDate()
            kaistaHaitta = alue.kaistaHaitta
            kaistaPituusHaitta = alue.kaistaPituusHaitta
            meluHaitta = alue.meluHaitta
            polyHaitta = alue.polyHaitta
            tarinaHaitta = alue.tarinaHaitta
            nimi = alue.nimi
        }
    }
}
