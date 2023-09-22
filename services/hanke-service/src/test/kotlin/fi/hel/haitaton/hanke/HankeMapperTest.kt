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
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.create
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.geometria.Geometriat
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
            prop(Hanke::id).isEqualTo(entity.id)
            prop(Hanke::hankeTunnus).isEqualTo(entity.hankeTunnus)
            prop(Hanke::onYKTHanke).isEqualTo(entity.onYKTHanke)
            prop(Hanke::nimi).isEqualTo(entity.nimi)
            prop(Hanke::kuvaus).isEqualTo(entity.kuvaus)
            prop(Hanke::vaihe).isEqualTo(entity.vaihe)
            prop(Hanke::suunnitteluVaihe).isEqualTo(entity.suunnitteluVaihe)
            prop(Hanke::version).isEqualTo(entity.version)
            prop(Hanke::createdAt).isEqualTo(entity.createdAt?.zonedDateTime())
            prop(Hanke::createdBy).isEqualTo(entity.createdByUserId)
            prop(Hanke::modifiedAt).isEqualTo(entity.modifiedAt?.zonedDateTime())
            prop(Hanke::modifiedBy).isEqualTo(entity.modifiedByUserId)
            prop(Hanke::status).isEqualTo(entity.status)
            prop(Hanke::generated).isEqualTo(entity.generated)
            prop(Hanke::omistajat).isEqualTo(expectedYhteystieto(entity, OMISTAJA, 1))
            prop(Hanke::toteuttajat).isEqualTo(expectedYhteystieto(entity, TOTEUTTAJA, 2))
            prop(Hanke::rakennuttajat).isEqualTo(expectedYhteystieto(entity, RAKENNUTTAJA, 3))
            prop(Hanke::muut).isEqualTo(expectedYhteystieto(entity, MUU, 4))
            prop(Hanke::tyomaaKatuosoite).isEqualTo(entity.tyomaaKatuosoite)
            prop(Hanke::tyomaaTyyppi).isEqualTo(entity.tyomaaTyyppi)
            prop(Hanke::alueet).isEqualTo(entity.listOfHankeAlueet.map { it.toDomain() })
            prop(Hanke::permissions).isNull()
        }
    }

    private fun HankeEntity.yhteystietoCreatedAt(contactType: ContactType) =
        listOfHankeYhteystieto.find { it.contactType == contactType }?.createdAt?.zonedDateTime()

    private fun HankeEntity.yhteystietoModifiedAt(contactType: ContactType) =
        listOfHankeYhteystieto.find { it.contactType == contactType }?.modifiedAt?.zonedDateTime()

    private fun HankealueEntity.toDomain(): Hankealue {
        return Hankealue(
            id = id,
            hankeId = hanke?.id,
            haittaAlkuPvm = haittaAlkuPvm?.atStartOfDay(TZ_UTC),
            haittaLoppuPvm = haittaLoppuPvm?.atStartOfDay(TZ_UTC),
            geometriat = geometry,
            kaistaHaitta = kaistaHaitta,
            kaistaPituusHaitta = kaistaPituusHaitta,
            meluHaitta = meluHaitta,
            polyHaitta = polyHaitta,
            tarinaHaitta = tarinaHaitta,
            nimi = nimi,
        )
    }

    private fun expectedYhteystieto(hankeEntity: HankeEntity, type: ContactType, id: Int) =
        mutableListOf(
            create(
                id = id,
                nimi = "$TEPPO_TESTI $type",
                email = "$type.$teppoEmail",
                createdAt = hankeEntity.yhteystietoCreatedAt(type),
                modifiedAt = hankeEntity.yhteystietoModifiedAt(type),
            )
        )
}
