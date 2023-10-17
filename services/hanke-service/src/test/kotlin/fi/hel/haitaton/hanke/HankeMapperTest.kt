package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.domain.SavedHanke
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.defaultYtunnus
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.geometria.Geometriat
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test

private const val MOCK_ID = 1

class HankeMapperTest {

    val geometry: Geometriat =
        "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()

    @Test
    fun `when entity contains all fields should map domain object correspondingly`() {
        val entity = HankeFactory.createEntity()

        val result = HankeMapper.domainFrom(entity, mapOf(MOCK_ID to geometry))

        assertThat(result).all {
            prop(SavedHanke::id).isEqualTo(entity.id)
            prop(SavedHanke::hankeTunnus).isEqualTo(entity.hankeTunnus)
            prop(SavedHanke::onYKTHanke).isEqualTo(entity.onYKTHanke)
            prop(SavedHanke::nimi).isEqualTo(entity.nimi)
            prop(SavedHanke::kuvaus).isEqualTo(entity.kuvaus)
            prop(SavedHanke::vaihe).isEqualTo(entity.vaihe)
            prop(SavedHanke::suunnitteluVaihe).isEqualTo(entity.suunnitteluVaihe)
            prop(SavedHanke::version).isEqualTo(entity.version)
            prop(SavedHanke::createdAt).isEqualTo(DateFactory.getStartDatetime())
            prop(SavedHanke::createdBy).isEqualTo(entity.createdByUserId)
            prop(SavedHanke::modifiedAt).isEqualTo(DateFactory.getEndDatetime())
            prop(SavedHanke::modifiedBy).isEqualTo(entity.modifiedByUserId)
            prop(SavedHanke::status).isEqualTo(entity.status)
            prop(SavedHanke::generated).isEqualTo(entity.generated)
            prop(SavedHanke::omistajat).isEqualTo(expectedYhteystieto(entity, OMISTAJA, 1))
            prop(SavedHanke::toteuttajat).isEqualTo(expectedYhteystieto(entity, TOTEUTTAJA, 2))
            prop(SavedHanke::rakennuttajat).isEqualTo(expectedYhteystieto(entity, RAKENNUTTAJA, 3))
            prop(SavedHanke::muut).isEqualTo(expectedYhteystieto(entity, MUU, 4))
            prop(SavedHanke::tyomaaKatuosoite).isEqualTo(entity.tyomaaKatuosoite)
            prop(SavedHanke::tyomaaTyyppi).isEqualTo(entity.tyomaaTyyppi)
            prop(SavedHanke::alueet).isEqualTo(expectedAlueet(entity.id, entity.hankeTunnus))
            prop(SavedHanke::permissions).isNull()
        }
    }

    private fun expectedYhteystieto(hankeEntity: HankeEntity, type: ContactType, id: Int) =
        mutableListOf(
            HankeYhteystietoFactory.create(
                id = id,
                nimi = "$TEPPO_TESTI $type",
                email = "$type.$teppoEmail",
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
                geometriat = geometry.apply { resetFeatureProperties(hankeTunnus) },
            )
        )
}
