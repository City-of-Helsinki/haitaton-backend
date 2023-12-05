package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.Haitta123
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.VaikutusAutoliikenteenKaistamaariin
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
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? =
            VaikutusAutoliikenteenKaistamaariin.KAKSI,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? =
            AutoliikenteenKaistavaikutustenPituus.NELJA,
        meluHaitta: Haitta123? = Haitta123.YKSI,
        polyHaitta: Haitta123? = Haitta123.KAKSI,
        tarinaHaitta: Haitta123? = Haitta123.KOLME,
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
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
        meluHaitta: Haitta123? = null,
        polyHaitta: Haitta123? = null,
        tarinaHaitta: Haitta123? = null,
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
        return HankealueEntity(
            id = alue.id!!,
            hanke = hankeEntity,
            geometriat = alue.geometriat?.id,
            haittaAlkuPvm = DateFactory.getStartDatetime().toLocalDate(),
            haittaLoppuPvm = DateFactory.getEndDatetime().toLocalDate(),
            kaistaHaitta = alue.kaistaHaitta,
            kaistaPituusHaitta = alue.kaistaPituusHaitta,
            meluHaitta = alue.meluHaitta,
            polyHaitta = alue.polyHaitta,
            tarinaHaitta = alue.tarinaHaitta,
            nimi = alue.nimi
        )
    }
}
