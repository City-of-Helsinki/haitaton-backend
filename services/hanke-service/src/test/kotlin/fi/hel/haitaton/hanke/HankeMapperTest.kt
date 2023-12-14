package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.defaultYtunnus
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test

private const val MOCK_ID = 1

class HankeMapperTest {

    @Test
    fun `when entity contains all fields should map domain object correspondingly`() {
        val entity = HankeFactory.createEntity()

        val result = HankeMapper.domainFrom(entity, mapOf(MOCK_ID to GeometriaFactory.create()))

        assertThat(result).all {
            prop(Hanke::id).isEqualTo(entity.id)
            prop(Hanke::hankeTunnus).isEqualTo(entity.hankeTunnus)
            prop(Hanke::onYKTHanke).isEqualTo(entity.onYKTHanke)
            prop(Hanke::nimi).isEqualTo(entity.nimi)
            prop(Hanke::kuvaus).isEqualTo(entity.kuvaus)
            prop(Hanke::vaihe).isEqualTo(entity.vaihe)
            prop(Hanke::version).isEqualTo(entity.version)
            prop(Hanke::createdAt).isEqualTo(DateFactory.getStartDatetime())
            prop(Hanke::createdBy).isEqualTo(entity.createdByUserId)
            prop(Hanke::modifiedAt).isEqualTo(DateFactory.getEndDatetime())
            prop(Hanke::modifiedBy).isEqualTo(entity.modifiedByUserId)
            prop(Hanke::status).isEqualTo(entity.status)
            prop(Hanke::generated).isEqualTo(entity.generated)
            prop(Hanke::omistajat).isEqualTo(expectedYhteystieto(entity, OMISTAJA, 1))
            prop(Hanke::toteuttajat).isEqualTo(expectedYhteystieto(entity, TOTEUTTAJA, 2))
            prop(Hanke::rakennuttajat).isEqualTo(expectedYhteystieto(entity, RAKENNUTTAJA, 3))
            prop(Hanke::muut).isEqualTo(expectedYhteystieto(entity, MUU, 4))
            prop(Hanke::tyomaaKatuosoite).isEqualTo(entity.tyomaaKatuosoite)
            prop(Hanke::tyomaaTyyppi).isEqualTo(entity.tyomaaTyyppi)
            prop(Hanke::alueet).isEqualTo(expectedAlueet(entity.id, entity.hankeTunnus))
        }
    }

    private fun expectedYhteystieto(hankeEntity: HankeEntity, type: ContactType, id: Int) =
        mutableListOf(
            HankeYhteystietoFactory.create(
                id = id,
                nimi = "$TEPPO_TESTI $type",
                email = "$type.$TEPPO_EMAIL",
                tyyppi = YRITYS,
                ytunnus = defaultYtunnus,
                createdAt = hankeEntity.yhteystietoCreatedAt(type),
                modifiedAt = hankeEntity.yhteystietoModifiedAt(type),
            )
        )

    private fun HankeEntity.yhteystietoCreatedAt(contactType: ContactType) =
        listOfHankeYhteystieto
            .find { it.contactType == contactType }
            ?.createdAt
            ?.let { ZonedDateTime.of(it, TZ_UTC) }

    private fun HankeEntity.yhteystietoModifiedAt(contactType: ContactType) =
        listOfHankeYhteystieto
            .find { it.contactType == contactType }
            ?.modifiedAt
            ?.let { ZonedDateTime.of(it, TZ_UTC) }

    private fun expectedAlueet(hankeId: Int?, hankeTunnus: String) =
        listOf(
            HankealueFactory.create(
                hankeId = hankeId,
                haittaAlkuPvm = DateFactory.getStartDatetime().toLocalDate().atStartOfDay(TZ_UTC),
                haittaLoppuPvm = DateFactory.getEndDatetime().toLocalDate().atStartOfDay(TZ_UTC),
                geometriat =
                    GeometriaFactory.create().apply { resetFeatureProperties(hankeTunnus) },
            )
        )
}
