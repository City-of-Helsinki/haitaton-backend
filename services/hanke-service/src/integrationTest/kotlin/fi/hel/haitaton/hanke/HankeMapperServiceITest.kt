package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

class HankeMapperServiceITest(
    @Autowired private val hankeMapperService: HankeMapperService,
    @Autowired private val hankeFactory: HankeFactory,
) : IntegrationTest() {

    private val hankeTunnus = "HAI23-13"

    @Nested
    inner class MinimalDomainFrom {

        @Test
        @Transactional
        fun `creates minimal domain object`() {
            val savedHanke =
                hankeFactory
                    .builder(USERNAME)
                    .withYhteystiedot()
                    .withHankealue()
                    .saveEntity(HankeStatus.PUBLIC)

            val result = hankeMapperService.minimalDomainFrom(savedHanke)

            assertThat(result).all {
                prop(Hanke::id).isEqualTo(savedHanke.id)
                prop(Hanke::hankeTunnus).isEqualTo(savedHanke.hankeTunnus)
                prop(Hanke::nimi).isEqualTo(savedHanke.nimi)
                prop(Hanke::generated).isEqualTo(savedHanke.generated)
                // Minimal fields should be null
                prop(Hanke::onYKTHanke).isNull()
                prop(Hanke::kuvaus).isNull()
                prop(Hanke::vaihe).isNull()
                prop(Hanke::version).isNull()
                prop(Hanke::createdAt).isNull()
                prop(Hanke::modifiedAt).isNull()
                prop(Hanke::status).isNull()
                prop(Hanke::omistajat).isEmpty()
                prop(Hanke::rakennuttajat).isEmpty()
                prop(Hanke::toteuttajat).isEmpty()
                prop(Hanke::muut).isEmpty()
                // Areas should be mapped
                prop(Hanke::alueet).hasSize(1)
            }
        }

        @Test
        @Transactional
        fun `should handle entity with multiple areas and geometries`() {
            val savedHanke =
                hankeFactory
                    .builder(USERNAME)
                    .withYhteystiedot()
                    .withHankealue(
                        alue =
                            HankealueFactory.create(
                                id = 1,
                                geometriat = GeometriaFactory.create(1, GeometriaFactory.polygon()),
                            )
                    )
                    .withHankealue(
                        alue =
                            HankealueFactory.create(
                                id = 2,
                                geometriat =
                                    GeometriaFactory.create(2, GeometriaFactory.secondPolygon()),
                            )
                    )
                    .saveEntity(HankeStatus.PUBLIC)

            val result = hankeMapperService.minimalDomainFrom(savedHanke)

            assertThat(result.alueet).hasSize(2)
            result.alueet.forEach { alue ->
                assertThat(alue.geometriat).isNotNull()
                assertThat(alue.hankeId).isEqualTo(savedHanke.id)
            }
        }
    }

    @Nested
    inner class DomainFrom {

        @Test
        @Transactional
        fun `creates a full domain object with all fields mapped`() {
            val savedHanke =
                hankeFactory
                    .builder(USERNAME)
                    .withYhteystiedot()
                    .withHankealue()
                    .saveEntity(HankeStatus.PUBLIC)

            val result = hankeMapperService.domainFrom(savedHanke)

            assertThat(result).all {
                prop(Hanke::id).isEqualTo(savedHanke.id)
                prop(Hanke::hankeTunnus).isEqualTo(savedHanke.hankeTunnus)
                prop(Hanke::nimi).isEqualTo(savedHanke.nimi)
                prop(Hanke::onYKTHanke).isEqualTo(savedHanke.onYKTHanke)
                prop(Hanke::kuvaus).isEqualTo(savedHanke.kuvaus)
                prop(Hanke::vaihe).isEqualTo(savedHanke.vaihe)
                prop(Hanke::version).isEqualTo(savedHanke.version)
                prop(Hanke::status).isEqualTo(savedHanke.status)
                prop(Hanke::generated).isEqualTo(savedHanke.generated)
                prop(Hanke::createdBy).isEqualTo(savedHanke.createdByUserId)
                prop(Hanke::modifiedBy).isEqualTo(savedHanke.modifiedByUserId)
                prop(Hanke::alueet).hasSize(1)
            }
            assertThat(result).all {
                prop(Hanke::omistajat).hasSize(1)
                prop(Hanke::rakennuttajat).hasSize(1)
                prop(Hanke::toteuttajat).hasSize(1)
                prop(Hanke::muut).hasSize(1)
            }
            result.omistajat.first().let { contact ->
                assertThat(contact.nimi).isNotNull()
                assertThat(contact.email).isNotNull()
                assertThat(contact.puhelinnumero).isNotNull()
            }
        }

        @Test
        @Transactional
        fun `should preserve area-specific data in full mapping`() {
            val savedHanke =
                hankeFactory
                    .builder(USERNAME)
                    .withYhteystiedot()
                    .withHankealue()
                    .saveEntity(HankeStatus.PUBLIC)
            val hankealue = savedHanke.alueet.first()

            val result = hankeMapperService.domainFrom(savedHanke)

            val mappedAlue = result.alueet.first()
            assertThat(mappedAlue).all {
                prop(SavedHankealue::id).isEqualTo(hankealue.id)
                prop(SavedHankealue::hankeId).isEqualTo(savedHanke.id)
                prop(SavedHankealue::nimi).isEqualTo(hankealue.nimi)
                prop(SavedHankealue::geometriat).isNotNull()
                prop(SavedHankealue::haittaAlkuPvm).isNotNull()
                prop(SavedHankealue::haittaLoppuPvm).isNotNull()
                // Full mapping should include haitta fields
                prop(SavedHankealue::kaistaHaitta).isNotNull()
                prop(SavedHankealue::kaistaPituusHaitta).isNotNull()
                prop(SavedHankealue::meluHaitta).isNotNull()
                prop(SavedHankealue::polyHaitta).isNotNull()
                prop(SavedHankealue::tarinaHaitta).isNotNull()
                prop(SavedHankealue::haittojenhallintasuunnitelma).isNotNull()
            }
        }
    }
}
