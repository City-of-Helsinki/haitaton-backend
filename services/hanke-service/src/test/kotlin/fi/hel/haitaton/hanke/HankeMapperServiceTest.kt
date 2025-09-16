package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeYhteyshenkiloFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.geometria.Geometriat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val MOCK_ID = 1

class HankeMapperServiceTest {

    private val hankealueService = mockk<HankealueService>()
    private val hankeMapperService = HankeMapperService(hankealueService)

    @Nested
    inner class MinimalDomainFromWithGeometries {
        @Test
        fun `when entity contains all fields should map domain object correspondingly`() {
            val entity = HankeFactory.createEntity()

            val result =
                hankeMapperService.minimalDomainFrom(
                    entity,
                    mapOf(MOCK_ID to GeometriaFactory.create()),
                )

            assertThat(result).all {
                prop(Hanke::id).isEqualTo(entity.id)
                prop(Hanke::hankeTunnus).isEqualTo(entity.hankeTunnus)
                prop(Hanke::onYKTHanke).isNull()
                prop(Hanke::nimi).isEqualTo(entity.nimi)
                prop(Hanke::kuvaus).isNull()
                prop(Hanke::vaihe).isNull()
                prop(Hanke::version).isNull()
                prop(Hanke::createdAt).isNull()
                prop(Hanke::createdBy).isNull()
                prop(Hanke::modifiedAt).isNull()
                prop(Hanke::modifiedBy).isNull()
                prop(Hanke::deletionDate).isNull()
                prop(Hanke::status).isNull()
                prop(Hanke::generated).isEqualTo(entity.generated)
                prop(Hanke::omistajat).isEmpty()
                prop(Hanke::toteuttajat).isEmpty()
                prop(Hanke::rakennuttajat).isEmpty()
                prop(Hanke::muut).isEmpty()
                prop(Hanke::tyomaaKatuosoite).isNull()
                prop(Hanke::tyomaaTyyppi).isEmpty()
                prop(Hanke::alueet).isEqualTo(expectedAlueet(entity.id, entity.hankeTunnus))
            }
        }

        private fun expectedAlueet(hankeId: Int?, hankeTunnus: String) =
            listOf(
                HankealueFactory.create(
                    hankeId = hankeId,
                    haittaAlkuPvm =
                        DateFactory.getStartDatetime().toLocalDate().atStartOfDay(TZ_UTC),
                    haittaLoppuPvm =
                        DateFactory.getEndDatetime().toLocalDate().atStartOfDay(TZ_UTC),
                    geometriat =
                        GeometriaFactory.create().apply { resetFeatureProperties(hankeTunnus) },
                    kaistaHaitta = null,
                    kaistaPituusHaitta = null,
                    meluHaitta = null,
                    polyHaitta = null,
                    tarinaHaitta = null,
                    tormaystarkasteluTulos = HaittaFactory.tormaystarkasteluTulos(),
                    haittojenhallintasuunnitelma = null,
                )
            )
    }

    @Nested
    inner class DomainFromWithGeometries {
        @Test
        fun `when entity contains all fields should map domain object correspondingly`() {
            val entity = HankeFactory.createEntity()

            val result =
                hankeMapperService.domainFrom(entity, mapOf(MOCK_ID to GeometriaFactory.create()))

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
                prop(Hanke::deletionDate).isEqualTo(entity.deletionDate())
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
                        ytunnus = HankeYhteystietoFactory.DEFAULT_YTUNNUS,
                        createdAt = hankeEntity.yhteystietoCreatedAt(type),
                        modifiedAt = hankeEntity.yhteystietoModifiedAt(type),
                    )
                    .withYhteyshenkilo(id * 2)
                    .withYhteyshenkilo(id * 2 + 1)
            )

        private fun HankeEntity.yhteystietoCreatedAt(contactType: ContactType) =
            yhteystiedot
                .find { it.contactType == contactType }
                ?.createdAt
                ?.let { ZonedDateTime.of(it, TZ_UTC) }

        private fun HankeEntity.yhteystietoModifiedAt(contactType: ContactType) =
            yhteystiedot
                .find { it.contactType == contactType }
                ?.modifiedAt
                ?.let { ZonedDateTime.of(it, TZ_UTC) }

        private fun expectedAlueet(hankeId: Int?, hankeTunnus: String) =
            listOf(
                HankealueFactory.create(
                    hankeId = hankeId,
                    haittaAlkuPvm =
                        DateFactory.getStartDatetime().toLocalDate().atStartOfDay(TZ_UTC),
                    haittaLoppuPvm =
                        DateFactory.getEndDatetime().toLocalDate().atStartOfDay(TZ_UTC),
                    geometriat =
                        GeometriaFactory.create().apply { resetFeatureProperties(hankeTunnus) },
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(),
                )
            )
    }

    @Nested
    inner class DomainFrom {
        @Test
        fun `should call hankealueService and domainFrom with correct parameters`() {
            val hankeEntity = HankeFactory.createEntity()
            val geometriaData = mapOf(MOCK_ID to GeometriaFactory.create())
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns geometriaData

            val result = hankeMapperService.domainFrom(hankeEntity)

            verify { hankealueService.geometryMapFrom(hankeEntity.alueet) }
            // Verify the result is a complete domain object (not minimal)
            assertThat(result).all {
                prop(Hanke::id).isEqualTo(hankeEntity.id)
                prop(Hanke::hankeTunnus).isEqualTo(hankeEntity.hankeTunnus)
                prop(Hanke::onYKTHanke).isEqualTo(hankeEntity.onYKTHanke)
                prop(Hanke::nimi).isEqualTo(hankeEntity.nimi)
                prop(Hanke::kuvaus).isEqualTo(hankeEntity.kuvaus)
                prop(Hanke::vaihe).isEqualTo(hankeEntity.vaihe)
                prop(Hanke::version).isEqualTo(hankeEntity.version)
                prop(Hanke::status).isEqualTo(hankeEntity.status)
            }
        }

        @Test
        fun `should handle entity with empty alueet`() {
            val hankeEntity = HankeFactory.createEntity().apply { alueet = mutableListOf() }
            val geometriaData = emptyMap<Int, Geometriat?>()
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns geometriaData

            val result = hankeMapperService.domainFrom(hankeEntity)

            assertThat(result.alueet).isEmpty()
        }

        @Test
        fun `should handle null geometriat in geometry data`() {
            val hankeEntity = HankeFactory.createEntity()
            val geometriaDataWithNull = mapOf(MOCK_ID to null)
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns
                geometriaDataWithNull

            val result = hankeMapperService.domainFrom(hankeEntity)

            // Should still create areas even with null geometry
            assertThat(result.alueet).hasSize(1)
        }
    }

    @Nested
    inner class MinimalDomainFrom {
        @Test
        fun `should call hankealueService and minimalDomainFrom with correct parameters`() {
            val hankeEntity = HankeFactory.createEntity()
            val geometriaData = mapOf(MOCK_ID to GeometriaFactory.create())
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns geometriaData

            val result = hankeMapperService.minimalDomainFrom(hankeEntity)

            verify { hankealueService.geometryMapFrom(hankeEntity.alueet) }
            // Verify the result is minimal (null fields)
            assertThat(result).all {
                prop(Hanke::id).isEqualTo(hankeEntity.id)
                prop(Hanke::hankeTunnus).isEqualTo(hankeEntity.hankeTunnus)
                prop(Hanke::nimi).isEqualTo(hankeEntity.nimi)
                // These should be null in minimal version
                prop(Hanke::onYKTHanke).isNull()
                prop(Hanke::kuvaus).isNull()
                prop(Hanke::vaihe).isNull()
                prop(Hanke::version).isNull()
                prop(Hanke::createdAt).isNull()
                prop(Hanke::createdBy).isNull()
                prop(Hanke::modifiedAt).isNull()
                prop(Hanke::modifiedBy).isNull()
                prop(Hanke::status).isNull()
            }
        }

        @Test
        fun `should preserve generated field in minimal mapping`() {
            val hankeEntity = HankeFactory.createEntity().apply { generated = true }
            val geometriaData = mapOf(MOCK_ID to GeometriaFactory.create())
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns geometriaData

            val result = hankeMapperService.minimalDomainFrom(hankeEntity)

            assertThat(result.generated).isEqualTo(true)
        }

        @Test
        fun `should handle multiple alueet correctly`() {
            val hankeEntity = HankeFactory.createEntity()
            val additionalAlue =
                HankealueFactory.createHankeAlueEntity(mockId = 2, hankeEntity = hankeEntity)
            hankeEntity.alueet.add(additionalAlue)

            val geometriaData =
                mapOf(MOCK_ID to GeometriaFactory.create(), 2 to GeometriaFactory.create())
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns geometriaData

            val result = hankeMapperService.minimalDomainFrom(hankeEntity)

            assertThat(result.alueet).hasSize(2)
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should map hankeTunnus correctly`() {
            val hankeEntity = HankeFactory.createEntity()
            every { hankealueService.geometryMapFrom(any()) } returns emptyMap()

            val result = hankeMapperService.minimalDomainFrom(hankeEntity)

            assertThat(result.hankeTunnus).isEqualTo(hankeEntity.hankeTunnus)
        }

        @Test
        fun `should handle entity with contact types in full mapping`() {
            val hankeEntity = HankeFactory.createEntity()
            val geometriaData = mapOf(MOCK_ID to GeometriaFactory.create())
            every { hankealueService.geometryMapFrom(hankeEntity.alueet) } returns geometriaData

            val result = hankeMapperService.domainFrom(hankeEntity)

            // Verify contact types are mapped (not testing exact content, just that they exist)
            assertThat(result).all {
                prop(Hanke::omistajat).hasSize(1)
                prop(Hanke::toteuttajat).hasSize(1)
                prop(Hanke::rakennuttajat).hasSize(1)
                prop(Hanke::muut).hasSize(1)
            }
        }

        @Test
        fun `should handle entity with zero id`() {
            val hankeEntity = HankeFactory.createEntity().apply { id = 0 }
            every { hankealueService.geometryMapFrom(any()) } returns emptyMap()

            val result = hankeMapperService.minimalDomainFrom(hankeEntity)

            assertThat(result.id).isEqualTo(0)
        }
    }
}
