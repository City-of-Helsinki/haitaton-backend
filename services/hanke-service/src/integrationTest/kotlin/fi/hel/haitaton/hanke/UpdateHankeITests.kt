package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.first
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.ExpectedHankeLogObject.expectedHankeLogObject
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.ModifyHankeRequest
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeBuilder.Companion.toModifyRequest
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeYhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.permissions.HankeKayttajaNotFoundException
import fi.hel.haitaton.hanke.test.Asserts.isRecentZDT
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasTargetType
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.tormaystarkastelu.Autoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus.PITUUS_500_METRIA_TAI_ENEMMAN
import fi.hel.haitaton.hanke.tormaystarkastelu.IndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.LiikennehaittaindeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

private const val NAME_1 = "etu1 suku1"
private const val NAME_2 = "etu2 suku2"
private const val NAME_3 = "etu3 suku3"
private const val NAME_SOMETHING = "Som Et Hing"

class UpdateHankeITests(
    @Autowired private val hankeService: HankeService,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hankeYhteyshenkiloRepository: HankeYhteyshenkiloRepository,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : IntegrationTest() {

    @Test
    fun `updates metadata fields when something changes`() {
        val hanke = hankeFactory.builder(USERNAME).create()
        assertThat(hanke).all {
            prop(Hanke::version).isEqualTo(0)
            prop(Hanke::createdAt).isRecentZDT()
            prop(Hanke::createdBy).isEqualTo(USERNAME)
            prop(Hanke::modifiedAt).isEqualTo(hanke.createdAt)
            prop(Hanke::modifiedBy).isEqualTo(hanke.createdBy)
        }
        val request = hanke.toModifyRequest().copy(kuvaus = "New description")

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result).all {
            prop(Hanke::version).isEqualTo(1)
            prop(Hanke::createdAt).isEqualTo(hanke.createdAt)
            prop(Hanke::createdBy).isEqualTo(hanke.createdBy)
            prop(Hanke::modifiedAt).isNotNull().isBetween(hanke.createdAt, ZonedDateTime.now())
            prop(Hanke::modifiedBy).isNotNull().isEqualTo(USERNAME)
        }
        assertThat(hankeService.loadHanke(result.hankeTunnus)).isEqualTo(result)
    }

    @Test
    fun `updates metadata fields when nothing changes`() {
        val hanke = hankeFactory.builder(USERNAME).create()
        assertThat(hanke).all {
            prop(Hanke::version).isEqualTo(0)
            prop(Hanke::createdAt).isRecentZDT()
            prop(Hanke::createdBy).isEqualTo(USERNAME)
            prop(Hanke::modifiedAt).isEqualTo(hanke.createdAt)
            prop(Hanke::modifiedBy).isEqualTo(hanke.createdBy)
        }
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result).all {
            prop(Hanke::version).isEqualTo(1)
            prop(Hanke::createdAt).isEqualTo(hanke.createdAt)
            prop(Hanke::createdBy).isEqualTo(hanke.createdBy)
            prop(Hanke::modifiedAt).isNotNull().isBetween(hanke.createdAt, ZonedDateTime.now())
            prop(Hanke::modifiedBy).isNotNull().isEqualTo(USERNAME)
        }
        assertThat(hankeService.loadHanke(result.hankeTunnus)).isEqualTo(result)
    }

    @Test
    fun `updates hanke status when given full data`() {
        val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
        assertThat(hanke.status).isEqualTo(HankeStatus.DRAFT)
        hanke.withYhteystiedot { id = null }
        val request = hanke.toModifyRequest()

        val returnedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(returnedHanke.status).isEqualTo(HankeStatus.PUBLIC)
    }

    @Test
    fun `resets feature properties`() {
        val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
        val request =
            hanke.toModifyRequest().apply {
                this.alueet[0].geometriat?.featureCollection?.features?.forEach {
                    it.properties["something"] = "fishy"
                }
            }

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertFeaturePropertiesIsReset(result, mapOf("hankeTunnus" to result.hankeTunnus))
    }

    @Test
    fun `doesn't revert to a draft`() {
        val hanke = hankeFactory.builder(USERNAME).withYhteystiedot().withHankealue().save()
        assertThat(hanke.status).isEqualTo(HankeStatus.PUBLIC)
        val request = hanke.toModifyRequest().copy(tyomaaKatuosoite = "")

        val exception =
            assertThrows<HankeArgumentException> {
                hankeService.updateHanke(hanke.hankeTunnus, request)
            }

        assertThat(exception).hasMessage("A public hanke didn't have all mandatory fields filled.")
    }

    @Test
    fun `throws exception when yhteystieto has unknown id`() {
        val hanke = hankeFactory.builder(USERNAME).withYhteystiedot().withHankealue().save()
        val rubbishId = hanke.extractYhteystiedot().mapNotNull { it.id }.max() + 1
        hanke.omistajat[0].id = rubbishId
        val request = hanke.toModifyRequest()

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HankeYhteystietoNotFoundException::class)
            messageContains("HankeYhteystieto not found for Hanke")
            messageContains(hanke.hankeTunnus)
            messageContains(hanke.id.toString())
            messageContains(rubbishId.toString())
        }
    }

    @Test
    fun `throws exception when yhteystieto is from another hanke`() {
        val hanke1 = hankeFactory.builder(USERNAME).withYhteystiedot().withHankealue().save()
        val hanke2 = hankeFactory.builder(USERNAME).withYhteystiedot().withHankealue().save()
        hanke1.omistajat[0].id = hanke2.omistajat[0].id
        val request = hanke1.toModifyRequest()

        val failure = assertFailure { hankeService.updateHanke(hanke1.hankeTunnus, request) }

        failure.all {
            hasClass(HankeYhteystietoNotFoundException::class)
            messageContains("HankeYhteystieto not found for Hanke")
            messageContains(hanke1.hankeTunnus)
            messageContains(hanke1.id.toString())
            messageContains(hanke2.omistajat[0].id.toString())
        }
    }

    @Test
    fun `adds new yhteystiedot`() {
        // Setup Hanke with one Yhteystieto:
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistaja(1).save()
        val ytid = hanke.omistajat[0].id!!
        hanke.withOmistaja(i = 2, id = null).withRakennuttaja(i = 3, id = null)
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        // Check that all 3 Yhteystietos are there:
        assertThat(result.omistajat).hasSize(2)
        assertThat(result.rakennuttajat).hasSize(1)
        // Check that the first Yhteystieto has not changed, and the two new ones are as
        // expected:
        // (Not checking all fields, just ensuring the code is not accidentally mixing whole
        // entries).
        assertThat(result.omistajat[0].id).isEqualTo(ytid)
        assertThat(result.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(result.omistajat[1].id).isNotEqualTo(ytid)
        assertThat(result.omistajat[1].nimi).isEqualTo(NAME_2)
        assertThat(result.rakennuttajat[0].id).isNotEqualTo(ytid)
        assertThat(result.rakennuttajat[0].id).isNotEqualTo(result.omistajat[1].id)
        assertThat(result.rakennuttajat[0].nimi).isEqualTo(NAME_3)

        // Use loadHanke and check it returns the same data:
        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            prop(Hanke::omistajat).isEqualTo(result.omistajat)
            prop(Hanke::rakennuttajat).isEqualTo(result.rakennuttajat)
            prop(Hanke::omistajat).first().all {
                prop(HankeYhteystieto::createdAt).isEqualTo(hanke.omistajat[0].createdAt)
                prop(HankeYhteystieto::createdBy).isEqualTo(hanke.omistajat[0].createdBy)
                // The original omistajat-entry was not modified, so modifiedXx-fields must not
                // get values:
                prop(HankeYhteystieto::modifiedAt).isNull()
                prop(HankeYhteystieto::modifiedBy).isNull()
            }
        }
    }

    @Test
    fun `does not create a duplicate when sending the same yhteystieto without an ID`() {
        // Old version of the Yhteystieto should get removed, id increases in response,
        // get-operation returns the new one.
        // NOTE: UI is not supposed to do that, but this situation came up during
        // development/testing, so it is a sort of regression test for the logic.
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistaja(1).save()
        val ytid = hanke.omistajat[0].id
        // Tweaking the returned Yhteystieto-object's id back to null, to make it look like new
        // one.
        hanke.omistajat[0].id = null
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result.omistajat).hasSize(1)
        assertThat(result.omistajat[0].id).isNotNull().isNotEqualTo(ytid)

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::omistajat).hasSameElementsAs(result.omistajat)
        }
    }

    @Test
    fun `updates the yhteystieto when changing one existing Yhteystieto in a group with two`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistajat(1, 2).save()
        val ytid1 = hanke.omistajat[0].id!!
        val ytid2 = hanke.omistajat[1].id!!
        assertThat(hanke.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(hanke.omistajat[1].nimi).isEqualTo(NAME_2)
        // Change a value:
        hanke.omistajat[1].nimi = NAME_SOMETHING
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        // Check that both entries kept their ids, and the only change is where expected
        assertThat(result.omistajat).hasSize(2)
        assertThat(result.omistajat[0].id).isEqualTo(ytid1)
        assertThat(result.omistajat[0].nimi).isEqualTo(NAME_1)
        // Check that audit modifiedXx-fields got updated:
        assertThat(result.omistajat[1]).all {
            prop(HankeYhteystieto::id).isEqualTo(ytid2)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_SOMETHING)
            prop(HankeYhteystieto::modifiedAt).isRecentZDT(Duration.ofMinutes(10))
            prop(HankeYhteystieto::modifiedBy).isNotNull().isEqualTo(USERNAME)
        }

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::omistajat).isEqualTo(result.omistajat)
        }
    }

    @Test
    fun `removes the yhteystieto when it's missing from the request`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistajat(1, 2).save()
        val ytid1 = hanke.omistajat[0].id!!
        hanke.omistajat.removeAt(1)
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result.omistajat).single().all {
            prop(HankeYhteystieto::id).isEqualTo(ytid1)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_1)
        }

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::omistajat).isEqualTo(result.omistajat)
        }
    }

    @Test
    fun `removes the correct yhteystieto when removing one of two identical yhteystietos in different groups`() {
        // Setup Hanke with two identical Yhteystietos in different group:
        val hanke =
            hankeFactory
                .builder(USERNAME)
                .withGeneratedOmistaja(1)
                .withGeneratedRakennuttaja(1)
                .save()
        val omistajaId = hanke.omistajat[0].id!!
        assertThat(hanke.rakennuttajat[0].id!!).isNotEqualTo(omistajaId)
        val request = hanke.toModifyRequest().copy(rakennuttajat = listOf())

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.rakennuttajat).isEmpty()
        assertThat(result.omistajat).single().all {
            prop(HankeYhteystieto::id).isEqualTo(omistajaId)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_1)
        }

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::rakennuttajat).isEmpty()
            prop(Hanke::omistajat).single().isEqualTo(result.omistajat[0])
        }
    }

    @Test
    fun `throws an exception when trying to add a non-existing yhteyshenkilo`() {
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistaja(1).save()
        val kayttajaId = UUID.fromString("c4f0e9a1-8308-47f6-9b26-177635e76b89")
        val omistaja =
            hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HankeKayttajaNotFoundException::class)
            messageContains("HankeKayttaja was not found")
            messageContains(kayttajaId.toString())
        }
    }

    @Test
    fun `throws an exception when trying to add an yhteyshenkilo from another hanke`() {
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistaja(1).save()
        val otherHanke = hankeFactory.builder(USERNAME).save()
        val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(otherHanke.id).id
        val omistaja =
            hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HankeKayttajaNotFoundException::class)
            messageContains("HankeKayttaja was not found")
            messageContains(kayttajaId.toString())
        }
    }

    @Test
    fun `adds an yhteyshenkilo when adding a new yhteystieto`() {
        val hanke = hankeFactory.builder(USERNAME).save()
        val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hanke.id).id
        val omistaja =
            HankeYhteystietoFactory.createDifferentiated(1)
                .toModifyRequest(id = null)
                .copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .single()
            .isEqualTo(HankeYhteyshenkiloFactory.kake(kayttajaId))
        val yhteyshenkiloIdentifiers = hankeYhteyshenkiloRepository.findIds()
        assertThat(yhteyshenkiloIdentifiers).single().all {
            prop(HankeYhteyshenkiloIdentifiers::kayttajaId).isEqualTo(kayttajaId)
            prop(HankeYhteyshenkiloIdentifiers::yhteystietoId)
                .isEqualTo(result.omistajat.first().id)
        }
    }

    @Test
    fun `adds an yhteyshenkilo when adding a hankekayttaja to an existing yhteystieto`() {
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistaja(1).save()
        val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hanke.id).id
        val omistaja =
            hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .single()
            .isEqualTo(HankeYhteyshenkiloFactory.kake(kayttajaId))
        val yhteyshenkiloIdentifiers = hankeYhteyshenkiloRepository.findIds()
        assertThat(yhteyshenkiloIdentifiers).single().all {
            prop(HankeYhteyshenkiloIdentifiers::kayttajaId).isEqualTo(kayttajaId)
            prop(HankeYhteyshenkiloIdentifiers::yhteystietoId).isEqualTo(omistaja.id)
        }
    }

    @Test
    fun `removes an yhteyshenkilo when removing the only one`() {
        val hankeId = hankeFactory.builder(USERNAME).saveWithYhteystiedot { omistaja() }.id
        val hanke = hankeService.loadHankeById(hankeId)!!
        val omistajaRequest = hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf())
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistajaRequest))

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat).single().prop(HankeYhteystieto::yhteyshenkilot).isEmpty()
        assertThat(hankeYhteyshenkiloRepository.findAll()).isEmpty()
    }

    @Test
    fun `adds and removes correct yhteyshenkilot in a complex setting`() {
        val hankeEntity = hankeFactory.builder().saveEntity()
        val hankeId = hankeEntity.id
        val kayttaja1 = hankeKayttajaFactory.saveIdentifiedUser(hankeId, sahkoposti = "kayttaja1")
        val kayttaja2 = hankeKayttajaFactory.saveIdentifiedUser(hankeId, sahkoposti = "kayttaja2")
        val kayttaja3 = hankeKayttajaFactory.saveIdentifiedUser(hankeId, sahkoposti = "kayttaja3")
        hankeFactory.addYhteystiedotTo(hankeEntity) {
            omistaja(kayttaja1, kayttaja2)
            rakennuttaja(yhteyshenkilot = arrayOf())
            rakennuttaja(yhteyshenkilot = arrayOf())
            toteuttaja(kayttaja3)
            muuYhteystieto(kayttaja1, kayttaja("kayttaja4"))
        }
        val hanke = hankeService.loadHankeById(hankeId)!!
        val newEmail = "new kayttaja"
        val newKayttaja = hankeKayttajaFactory.saveIdentifiedUser(hanke.id, sahkoposti = newEmail)
        // Remove kayttaja2 from omistaja and add new kayttaja
        val omistaja =
            hanke.omistajat[0]
                .toModifyRequest()
                .copy(yhteyshenkilot = listOf(kayttaja1.id, newKayttaja.id))
        // Add kayttaja1 and kayttaja2 to first rakennuttaja
        val rakennuttaja1 =
            hanke.rakennuttajat[0]
                .toModifyRequest()
                .copy(yhteyshenkilot = listOf(kayttaja1.id, kayttaja2.id))
        // Leave the other rakennuttaja without yhteyshenkilo
        val rakennuttaja2 = hanke.rakennuttajat[1].toModifyRequest()
        // Leave toteuttaja with kayttaja3
        val toteuttaja = hanke.toteuttajat[0].toModifyRequest()
        // Remove kayttaja1 and kayttaja4 from muu and add new kayttaja
        val muu = hanke.muut[0].toModifyRequest().copy(yhteyshenkilot = listOf(newKayttaja.id))
        val request =
            hanke
                .toModifyRequest()
                .copy(
                    omistajat = listOf(omistaja),
                    rakennuttajat = listOf(rakennuttaja1, rakennuttaja2),
                    toteuttajat = listOf(toteuttaja),
                    muut = listOf(muu),
                )

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .extracting { it.sahkoposti }
            .containsExactlyInAnyOrder("kayttaja1", newEmail)
        assertThat(result.rakennuttajat)
            .extracting { yhteystieto -> yhteystieto.yhteyshenkilot.map { it.sahkoposti } }
            .containsExactlyInAnyOrder(listOf<String>(), listOf("kayttaja1", "kayttaja2"))
        assertThat(result.toteuttajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .extracting { it.sahkoposti }
            .containsExactly("kayttaja3")
        assertThat(result.muut)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .extracting { it.sahkoposti }
            .containsExactly(newEmail)

        val yhteyshenkiloIdentifiers = hankeYhteyshenkiloRepository.findIds()
        assertThat(yhteyshenkiloIdentifiers).hasSize(6)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == omistaja.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(kayttaja1.id, newKayttaja.id)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == rakennuttaja1.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(kayttaja1.id, kayttaja2.id)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == rakennuttaja2.id })
            .isEmpty()
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == toteuttaja.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(kayttaja3.id)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == muu.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(newKayttaja.id)
    }

    @Test
    fun `updateHanke creates new hankealue`() {
        val alkuPvm = DateFactory.getStartDatetime()
        val loppuPvm = DateFactory.getStartDatetime()
        val createdHanke = hankeFactory.builder(USERNAME).withHankealue().save()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = loppuPvm,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.JATKUVA_MELUHAITTA,
                polyHaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
                tarinaHaitta = Tarinahaitta.SATUNNAINEN_TARINAHAITTA,
            )
        createdHanke.alueet.add(hankealue)
        val request = createdHanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(createdHanke.hankeTunnus, request)

        assertThat(updatedHanke.alueet).hasSize(2)
        val alue = updatedHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(alkuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(loppuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(
                VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA
            )
        assertThat(alue.kaistaPituusHaitta)
            .isEqualTo(AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA)
        assertThat(alue.meluHaitta).isEqualTo(Meluhaitta.JATKUVA_MELUHAITTA)
        assertThat(alue.polyHaitta).isEqualTo(Polyhaitta.TOISTUVA_POLYHAITTA)
        assertThat(alue.tarinaHaitta).isEqualTo(Tarinahaitta.SATUNNAINEN_TARINAHAITTA)
        assertThat(alue.geometriat).isNotNull()
    }

    @Test
    fun `updates hankealue name and keeps other data intact`() {
        val createdHanke = hankeFactory.builder(USERNAME).withHankealue().save()
        val hankealue = createdHanke.alueet[0]
        assertThat(hankealue.nimi).isEqualTo("Hankealue 1")
        val request = createdHanke.toModifyRequest()
        val updatedRequest =
            request.copy(alueet = listOf(request.alueet[0].copy(nimi = "Changed Name")))

        val updateHankeResult = hankeService.updateHanke(createdHanke.hankeTunnus, updatedRequest)

        assertThat(updateHankeResult)
            .transform { it.copy(modifiedAt = null) }
            .isEqualTo(createdHanke.copy(version = 2, modifiedAt = null))
        assertThat(updateHankeResult.alueet).single().all {
            transform { it.copy(geometriat = null) }
                .isEqualTo(hankealue.copy(geometriat = null, nimi = "Changed Name"))
            prop(SavedHankealue::geometriat)
                .isNotNull()
                .prop(Geometriat::featureCollection)
                .isEqualTo(hankealue.geometriat!!.featureCollection)
        }
    }

    @Test
    fun `updates a nuisance control plan`() {
        val createdHanke = hankeFactory.builder(USERNAME).withHankealue().save()
        val hankealue = createdHanke.alueet[0]
        assertThat(hankealue.haittojenhallintasuunnitelma!!.size).isEqualTo(6)
        assertThat(hankealue.haittojenhallintasuunnitelma!![Haittojenhallintatyyppi.YLEINEN])
            .isEqualTo("Yleisten haittojen hallintasuunnitelma")
        hankealue.haittojenhallintasuunnitelma =
            HaittaFactory.createHaittojenhallintasuunnitelma(
                Haittojenhallintatyyppi.YLEINEN to "Uusi yleisten haittojen hallintasuunnitelma"
            )
        val request = createdHanke.toModifyRequest()

        val updateHankeResult = hankeService.updateHanke(createdHanke.hankeTunnus, request)

        val updatedHaittojenhallintasuunnitelma =
            updateHankeResult.alueet.single().haittojenhallintasuunnitelma!!
        assertThat(updatedHaittojenhallintasuunnitelma.size).isEqualTo(6)
        assertThat(updatedHaittojenhallintasuunnitelma[Haittojenhallintatyyppi.YLEINEN])
            .isEqualTo("Uusi yleisten haittojen hallintasuunnitelma")
    }

    @Test
    fun `removes a nuisance control plan`() {
        val createdHanke = hankeFactory.builder(USERNAME).withHankealue().save()
        val hankealue = createdHanke.alueet[0]
        assertThat(hankealue.haittojenhallintasuunnitelma!!.size).isEqualTo(6)
        hankealue.haittojenhallintasuunnitelma =
            HaittaFactory.createHaittojenhallintasuunnitelma(
                Haittojenhallintatyyppi.YLEINEN to null
            )
        val request = createdHanke.toModifyRequest()

        val updateHankeResult = hankeService.updateHanke(createdHanke.hankeTunnus, request)

        val updatedHaittojenhallintasuunnitelma =
            updateHankeResult.alueet.single().haittojenhallintasuunnitelma!!
        assertThat(updatedHaittojenhallintasuunnitelma.size).isEqualTo(5)
        assertThat(updatedHaittojenhallintasuunnitelma[Haittojenhallintatyyppi.YLEINEN]).isNull()
    }

    @Test
    fun `adds a new nuisance control plan`() {
        val createdHanke =
            hankeFactory
                .builder(USERNAME)
                .withHankealue(
                    haittojenhallintasuunnitelma =
                        HaittaFactory.createHaittojenhallintasuunnitelma(
                            Haittojenhallintatyyppi.YLEINEN to null
                        )
                )
                .save()
        val hankealue = createdHanke.alueet[0]
        assertThat(hankealue.haittojenhallintasuunnitelma!!.size).isEqualTo(5)
        assertThat(hankealue.haittojenhallintasuunnitelma!![Haittojenhallintatyyppi.YLEINEN])
            .isNull()
        hankealue.haittojenhallintasuunnitelma =
            HaittaFactory.createHaittojenhallintasuunnitelma(
                Haittojenhallintatyyppi.YLEINEN to "Uusi yleisten haittojen hallintasuunnitelma"
            )
        val request = createdHanke.toModifyRequest()

        val updateHankeResult = hankeService.updateHanke(createdHanke.hankeTunnus, request)

        val updatedHaittojenhallintasuunnitelma =
            updateHankeResult.alueet.single().haittojenhallintasuunnitelma!!
        assertThat(updatedHaittojenhallintasuunnitelma.size).isEqualTo(6)
        assertThat(updatedHaittojenhallintasuunnitelma[Haittojenhallintatyyppi.YLEINEN])
            .isEqualTo("Uusi yleisten haittojen hallintasuunnitelma")
    }

    @Test
    fun `removes hankealue and geometriat and nuisance control plan when saved alue missing from request`() {
        val alkuPvm = DateFactory.getStartDatetime()
        val loppuPvm = DateFactory.getStartDatetime()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = loppuPvm,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.SATUNNAINEN_MELUHAITTA,
                polyHaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
                tarinaHaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
                haittojenhallintasuunnitelma = null,
            )
        val hanke = hankeFactory.builder(USERNAME).withHankealue().withHankealue(hankealue).save()
        assertThat(hanke.alueet).hasSize(2)
        assertThat(hankealueCount()).isEqualTo(2)
        assertThat(geometriatCount()).isEqualTo(2)
        assertThat(haittojenhallintasuunnitelmaCount())
            .isEqualTo(Haittojenhallintatyyppi.entries.size)
        hanke.alueet.removeAt(0)
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(updatedHanke.alueet).single().all {
            prop(SavedHankealue::haittaAlkuPvm).isEqualTo(alkuPvm.truncatedTo(ChronoUnit.DAYS))
            prop(SavedHankealue::haittaLoppuPvm).isEqualTo(loppuPvm.truncatedTo(ChronoUnit.DAYS))
            prop(SavedHankealue::kaistaHaitta)
                .isEqualTo(
                    VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA
                )
            prop(SavedHankealue::kaistaPituusHaitta)
                .isEqualTo(AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA)
            prop(SavedHankealue::meluHaitta).isEqualTo(Meluhaitta.SATUNNAINEN_MELUHAITTA)
            prop(SavedHankealue::polyHaitta).isEqualTo(Polyhaitta.TOISTUVA_POLYHAITTA)
            prop(SavedHankealue::tarinaHaitta).isEqualTo(Tarinahaitta.JATKUVA_TARINAHAITTA)
            prop(SavedHankealue::geometriat).isNotNull()
        }
        val hankeFromDb = hankeService.loadHanke(hanke.hankeTunnus)
        assertThat(hankeFromDb?.alueet).isNotNull().hasSize(1)
        assertThat(hankealueCount()).isEqualTo(1)
        assertThat(geometriatCount()).isEqualTo(1)
        assertThat(haittojenhallintasuunnitelmaCount()).isEqualTo(0)
    }

    @Test
    fun `calculates tormaystarkastelu for each added alue`() {
        val hanke = hankeFactory.builder(USERNAME).save()
        assertThat(hanke.alueet).isEmpty()
        hanke.withHankealue()
        hanke.alueet.add(HankealueFactory.create(geometriat = GeometriaFactory.create()))
        assertThat(hanke.alueet).hasSize(2)
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.alueet).hasSize(2)
        assertThat(result.alueet.map { it.tormaystarkasteluTulos }).each {
            it.isNotNull().all {
                prop(TormaystarkasteluTulos::autoliikenne).all {
                    prop(Autoliikenneluokittelu::haitanKesto).isEqualTo(1)
                    prop(Autoliikenneluokittelu::katuluokka).isEqualTo(4)
                    prop(Autoliikenneluokittelu::liikennemaara).isEqualTo(5)
                    prop(Autoliikenneluokittelu::kaistahaitta).isEqualTo(1)
                    prop(Autoliikenneluokittelu::kaistapituushaitta).isEqualTo(2)
                    prop(Autoliikenneluokittelu::indeksi).isEqualTo(2.8f)
                }
                prop(TormaystarkasteluTulos::pyoraliikenneindeksi).isEqualTo(3.0f)
                prop(TormaystarkasteluTulos::linjaautoliikenneindeksi).isEqualTo(3.0f)
                prop(TormaystarkasteluTulos::raitioliikenneindeksi).isEqualTo(5.0f)
                prop(TormaystarkasteluTulos::liikennehaittaindeksi).all {
                    prop(LiikennehaittaindeksiType::tyyppi)
                        .isEqualTo(IndeksiType.RAITIOLIIKENNEINDEKSI)
                    prop(LiikennehaittaindeksiType::indeksi).isEqualTo(5.0f)
                }
            }
        }
    }

    @Test
    fun `recalculates tormaystarkastelu when hankealue is updated`() {
        val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
        val originalTormaystarkasteluTulos = hanke.alueet[0].tormaystarkasteluTulos!!
        val originalAutoliikenteenIndeksi = originalTormaystarkasteluTulos.autoliikenne.indeksi
        assertThat(originalTormaystarkasteluTulos.autoliikenne.kaistahaitta).isEqualTo(1)
        assertThat(originalTormaystarkasteluTulos.autoliikenne.kaistapituushaitta).isEqualTo(2)
        hanke.alueet[0] =
            HankealueFactory.create(
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin.USEITA_AJOSUUNTIA_POISTUU_KAYTOSTA,
                kaistaPituusHaitta = PITUUS_500_METRIA_TAI_ENEMMAN,
            )
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.alueet)
            .single()
            .prop(SavedHankealue::tormaystarkasteluTulos)
            .isNotNull()
            .prop(TormaystarkasteluTulos::autoliikenne)
            .all {
                prop(Autoliikenneluokittelu::haitanKesto)
                    .isEqualTo(originalTormaystarkasteluTulos.autoliikenne.haitanKesto)
                prop(Autoliikenneluokittelu::katuluokka)
                    .isEqualTo(originalTormaystarkasteluTulos.autoliikenne.katuluokka)
                prop(Autoliikenneluokittelu::liikennemaara)
                    .isEqualTo(originalTormaystarkasteluTulos.autoliikenne.liikennemaara)
                prop(Autoliikenneluokittelu::kaistahaitta).isEqualTo(5)
                prop(Autoliikenneluokittelu::kaistapituushaitta).isEqualTo(5)
                // increasing lane nuisance and lane length nuisance should increase the index
                prop(Autoliikenneluokittelu::indeksi).isGreaterThan(originalAutoliikenteenIndeksi)
            }
    }

    @Test
    fun `creates audit log entry for updated hanke`() {
        val hanke =
            hankeFactory
                .builder(USERNAME)
                .withTyomaaKatuosoite("Testikatu 1")
                .withTyomaaTyypit(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
                .save()
        val geometria = GeometriaFactory.create().apply { id = 67 }
        hanke.alueet.add(HankealueFactory.create(id = null, geometriat = geometria))
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        val expectedBefore =
            expectedHankeLogObject(hanke, hankeVersion = 1, alkuPvm = null, loppuPvm = null)
        val expectedAfter =
            expectedHankeLogObject(
                hanke,
                updatedHanke.alueet[0],
                hankeVersion = 2,
                tormaystarkasteluTulos = true,
            )
        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertThat(hankeLogs).single().isSuccess(Operation.UPDATE) {
            hasUserActor("test7358", TestUtils.MOCKED_IP)
            withTarget {
                hasId(hanke.id)
                hasTargetType(ObjectType.HANKE)
                prop(AuditLogTarget::objectBefore).given {
                    JSONAssert.assertEquals(expectedBefore, it, JSONCompareMode.NON_EXTENSIBLE)
                }
                prop(AuditLogTarget::objectAfter).given {
                    JSONAssert.assertEquals(expectedAfter, it, JSONCompareMode.NON_EXTENSIBLE)
                }
            }
        }
    }

    @Test
    fun `creates audit log entry when geometria is updated in hankealue`() {
        val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()
        hanke.alueet[0].geometriat = GeometriaFactory.create(hanke.alueet[0].geometriat!!.id!!)
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        val expectedLogBefore =
            expectedHankeLogObject(
                hanke,
                hanke.alueet[0],
                hankeVersion = 1,
                tormaystarkasteluTulos = true,
            )
        val expectedLogAfter =
            expectedHankeLogObject(
                updatedHanke,
                updatedHanke.alueet[0],
                hankeVersion = 2,
                geometriaVersion = 1,
                tormaystarkasteluTulos = true,
            )
        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertThat(hankeLogs).single().isSuccess(Operation.UPDATE) {
            hasUserActor("test7358", TestUtils.MOCKED_IP)
            withTarget {
                hasId(hanke.id)
                hasTargetType(ObjectType.HANKE)
                prop(AuditLogTarget::objectBefore).given {
                    JSONAssert.assertEquals(expectedLogBefore, it, JSONCompareMode.NON_EXTENSIBLE)
                }
                prop(AuditLogTarget::objectAfter).given {
                    JSONAssert.assertEquals(expectedLogAfter, it, JSONCompareMode.NON_EXTENSIBLE)
                }
            }
        }
    }

    @Test
    fun `creates audit log entry when nuisance control plan is updated in hankealue`() {
        val hanke =
            hankeFactory.builder(USERNAME).withHankealue(haittojenhallintasuunnitelma = null).save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()
        hanke.alueet[0].haittojenhallintasuunnitelma =
            HaittaFactory.createHaittojenhallintasuunnitelma()
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        val expectedLogBefore =
            expectedHankeLogObject(
                hanke,
                hanke.alueet[0].apply { haittojenhallintasuunnitelma = null },
                hankeVersion = 1,
                tormaystarkasteluTulos = true,
            )
        val expectedLogAfter =
            expectedHankeLogObject(
                updatedHanke,
                updatedHanke.alueet[0],
                hankeVersion = 2,
                geometriaVersion = 1,
                tormaystarkasteluTulos = true,
            )
        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertThat(hankeLogs).single().isSuccess(Operation.UPDATE) {
            hasUserActor("test7358", TestUtils.MOCKED_IP)
            withTarget {
                hasId(hanke.id)
                hasTargetType(ObjectType.HANKE)
                prop(AuditLogTarget::objectBefore).given {
                    JSONAssert.assertEquals(expectedLogBefore, it, JSONCompareMode.NON_EXTENSIBLE)
                }
                prop(AuditLogTarget::objectAfter).given {
                    JSONAssert.assertEquals(expectedLogAfter, it, JSONCompareMode.NON_EXTENSIBLE)
                }
            }
        }
    }

    @Test
    fun `creates audit log entry even if there are no changes`() {
        val hanke =
            hankeFactory
                .builder(USERNAME)
                .withTyomaaKatuosoite("Testikatu 1")
                .withTyomaaTyypit(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
                .save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        val request = hanke.toModifyRequest()

        hankeService.updateHanke(hanke.hankeTunnus, request)

        val expectedBefore =
            expectedHankeLogObject(hanke, hankeVersion = 1, alkuPvm = null, loppuPvm = null)
        val expectedAfter =
            expectedHankeLogObject(hanke, hankeVersion = 2, alkuPvm = null, loppuPvm = null)
        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertThat(hankeLogs).single().isSuccess(Operation.UPDATE) {
            withTarget {
                prop(AuditLogTarget::objectBefore).given {
                    JSONAssert.assertEquals(expectedBefore, it, JSONCompareMode.NON_EXTENSIBLE)
                }
                prop(AuditLogTarget::objectAfter).given {
                    JSONAssert.assertEquals(expectedAfter, it, JSONCompareMode.NON_EXTENSIBLE)
                }
            }
        }
    }

    @Test
    fun `throws exception when removing a hankealue with a johtoselvityshakemus on it`() {
        val firstArea = HankealueFactory.create(nimi = "Alue hakemuksella")
        val geometriat = GeometriaFactory.create(geometria = GeometriaFactory.fourthPolygon())
        val secondArea = HankealueFactory.create(nimi = "Ilman hakemusta", geometriat = geometriat)
        val hankeEntity =
            hankeFactory.builder().withHankealue(firstArea).withHankealue(secondArea).saveEntity()
        val hanke = hankeService.loadHanke(hankeEntity.hankeTunnus)!!
        val hakemus =
            hakemusFactory
                .builder(hankeEntity)
                .withMandatoryFields(
                    hankealue = hanke.alueet.first { it.nimi == "Alue hakemuksella" }
                )
                .save()
        hanke.alueet.removeIf { it.nimi == "Alue hakemuksella" }
        val request = hanke.toModifyRequest()

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HakemusGeometryNotInsideHankeException::class)
            messageContains("Hakemus geometry doesn't match any hankealue")
            messageContains(
                "hakemus geometry=${hakemus.applicationData.areas!!.single().geometries().single().toJsonString()}"
            )
        }
    }

    @Test
    fun `throws exception when removing a hankealue with a kaivuilmoitus on it`() {
        val firstArea = HankealueFactory.create(nimi = "Alue hakemuksella")
        val geometriat = GeometriaFactory.create(geometria = GeometriaFactory.fourthPolygon())
        val secondArea = HankealueFactory.create(nimi = "Ilman hakemusta", geometriat = geometriat)
        val hankeEntity =
            hankeFactory.builder().withHankealue(firstArea).withHankealue(secondArea).saveEntity()
        val hanke = hankeService.loadHanke(hankeEntity.hankeTunnus)!!
        val hankealueWithHakemus = hanke.alueet.first { it.nimi == "Alue hakemuksella" }
        val hakemus =
            hakemusFactory
                .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                .withMandatoryFields(hankealue = hankealueWithHakemus)
                .save()
        hanke.alueet.remove(hankealueWithHakemus)
        val request = hanke.toModifyRequest()

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HakemusGeometryNotInsideHankeException::class)
            messageContains("Hakemus geometry is outside the associated hankealue")
            messageContains("hankealue=${hankealueWithHakemus.id}")
            messageContains(
                "hakemus geometry=${hakemus.applicationData.areas!!.single().geometries().single().toJsonString()}"
            )
        }
    }

    @Test
    fun `accepts the update when removing an area with no hakemus`() {
        val firstArea = HankealueFactory.create(nimi = "Alue johtoselvityshakemuksella")
        val geometriat1 = GeometriaFactory.create(geometria = GeometriaFactory.fourthPolygon())
        val secondArea = HankealueFactory.create(nimi = "Ilman hakemusta", geometriat = geometriat1)
        val geometriat2 = GeometriaFactory.create(geometria = GeometriaFactory.polygon())
        val thirdArea =
            HankealueFactory.create(nimi = "Alue kaivuilmoituksella", geometriat = geometriat2)
        val hankeEntity =
            hankeFactory
                .builder()
                .withHankealue(firstArea)
                .withHankealue(secondArea)
                .withHankealue(thirdArea)
                .saveEntity()
        val hanke = hankeService.loadHanke(hankeEntity.hankeTunnus)!!
        hakemusFactory
            .builder(hankeEntity)
            .withMandatoryFields(
                hankealue = hanke.alueet.first { it.nimi == "Alue johtoselvityshakemuksella" }
            )
            .save()
        hakemusFactory
            .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
            .withMandatoryFields(
                hankealue = hanke.alueet.first { it.nimi == "Alue kaivuilmoituksella" }
            )
            .save()
        hanke.alueet.removeIf { it.nimi == "Ilman hakemusta" }
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(updatedHanke.alueet)
            .extracting(SavedHankealue::nimi)
            .containsExactlyInAnyOrder("Alue johtoselvityshakemuksella", "Alue kaivuilmoituksella")
    }

    @ParameterizedTest
    @CsvSource("6,20", "5,21", ",20", ",21", "5,", "6,")
    fun `accepts the updated when the hankealue date covers the johtoselvityshakemus dates`(
        newStartDay: Int?,
        newEndDay: Int?,
    ) {
        val (hanketunnus, request) =
            setupDateTest(newStartDay, newEndDay, ApplicationType.CABLE_REPORT)

        val updatedHanke = hankeService.updateHanke(hanketunnus, request)

        assertThat(updatedHanke.alueet[0]).all {
            prop(Hankealue::haittaAlkuPvm)
                .isNotNull()
                .prop(ZonedDateTime::getDayOfMonth)
                .isEqualTo(newStartDay ?: 6)
            prop(Hankealue::haittaLoppuPvm)
                .isNotNull()
                .prop(ZonedDateTime::getDayOfMonth)
                .isEqualTo(newEndDay ?: 20)
        }
    }

    @ParameterizedTest
    @CsvSource("7,20", "6,19", "7,19", ",19", "7,")
    fun `throws an exception when new hankealue dates don't cover johtoselvityshakemus dates`(
        newStartDay: Int?,
        newEndDay: Int?,
    ) {
        val (hanketunnus, request) =
            setupDateTest(newStartDay, newEndDay, ApplicationType.CABLE_REPORT)

        val failure = assertFailure { hankeService.updateHanke(hanketunnus, request) }

        failure.all {
            hasClass(HankeArgumentException::class)
            messageContains("No hankealue covers the hakemusalue")
            messageContains("Hakemusalue 0: 2025-02-06 - 2025-02-20")
            val hankealueId = request.alueet[0].id
            // Nulls in the request means "no update"
            val loggedStartDay = newStartDay ?: 6
            val loggedEndDay = newEndDay ?: 20
            messageContains(
                "Hankealueet $hankealueId: 2025-02-0$loggedStartDay - 2025-02-$loggedEndDay"
            )
        }
    }

    @ParameterizedTest
    @CsvSource("6,20", "5,21", ",20", ",21", "5,", "6,")
    fun `accepts the updated when the hankealue date covers the kaivuilmoitus dates`(
        newStartDay: Int?,
        newEndDay: Int?,
    ) {
        val (hanketunnus, request) =
            setupDateTest(newStartDay, newEndDay, ApplicationType.EXCAVATION_NOTIFICATION)

        val updatedHanke = hankeService.updateHanke(hanketunnus, request)

        assertThat(updatedHanke.alueet[0]).all {
            prop(Hankealue::haittaAlkuPvm)
                .isNotNull()
                .prop(ZonedDateTime::getDayOfMonth)
                .isEqualTo(newStartDay ?: 6)
            prop(Hankealue::haittaLoppuPvm)
                .isNotNull()
                .prop(ZonedDateTime::getDayOfMonth)
                .isEqualTo(newEndDay ?: 20)
        }
    }

    @ParameterizedTest
    @CsvSource("6,19", "7,19", ",19", "7,", "7,20")
    fun `throws an exception when new hankealue dates don't cover kaivuilmoitus dates`(
        newStartDay: Int?,
        newEndDay: Int?,
    ) {
        val (hanketunnus, request) =
            setupDateTest(newStartDay, newEndDay, ApplicationType.EXCAVATION_NOTIFICATION)

        val failure = assertFailure { hankeService.updateHanke(hanketunnus, request) }

        failure.all {
            hasClass(HankeArgumentException::class)
            messageContains("Hankealue doesn't cover the hakemus dates")
            messageContains("Hakemusalue 0: 2025-02-06 - 2025-02-20")
            val hankealueId = request.alueet[0].id
            // Nulls in the request means "no update"
            val loggedStartDay = newStartDay ?: 6
            val loggedEndDay = newEndDay ?: 20
            messageContains(
                "Hankealue $hankealueId: 2025-02-0$loggedStartDay - 2025-02-$loggedEndDay"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `accepts the updated when the hakemus dates are nulls`(applicationType: ApplicationType) {
        val startDate = ZonedDateTime.parse("2025-02-06T12:00:00Z")
        val endDate = ZonedDateTime.parse("2025-02-20T12:00:00Z")
        val hankealue = HankealueFactory.create(haittaAlkuPvm = startDate, haittaLoppuPvm = endDate)
        val hankeEntity = hankeFactory.builder().withHankealue(hankealue).saveEntity()
        val hanke = hankeService.loadHanke(hankeEntity.hankeTunnus)!!
        hakemusFactory
            .builder(hankeEntity, applicationType)
            .withMandatoryFields(hanke.alueet[0])
            .withStartTime(null)
            .withEndTime(null)
            .save()
        val newStartDate = ZonedDateTime.parse("2025-02-07T12:00:00Z")
        val newEndDate = ZonedDateTime.parse("2025-02-19T12:00:00Z")
        hanke.alueet[0].haittaAlkuPvm = newStartDate
        hanke.alueet[0].haittaLoppuPvm = newEndDate
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(updatedHanke.alueet[0]).all {
            prop(Hankealue::haittaAlkuPvm)
                .isNotNull()
                .prop(ZonedDateTime::toLocalDate)
                .isEqualTo(newStartDate.toLocalDate())
            prop(Hankealue::haittaLoppuPvm)
                .isNotNull()
                .prop(ZonedDateTime::toLocalDate)
                .isEqualTo(newEndDate.toLocalDate())
        }
    }

    private fun setupDateTest(
        newStartDay: Int?,
        newEndDay: Int?,
        applicationType: ApplicationType,
    ): Pair<String, ModifyHankeRequest> {
        val startDate = ZonedDateTime.parse("2025-02-06T12:00:00Z")
        val endDate = ZonedDateTime.parse("2025-02-20T12:00:00Z")
        val hankealue = HankealueFactory.create(haittaAlkuPvm = startDate, haittaLoppuPvm = endDate)
        val hankeEntity = hankeFactory.builder().withHankealue(hankealue).saveEntity()
        val hanke = hankeService.loadHanke(hankeEntity.hankeTunnus)!!
        hakemusFactory
            .builder(hankeEntity, applicationType)
            .withMandatoryFields(hanke.alueet[0])
            .withStartTime(startDate)
            .withEndTime(endDate)
            .save()
        val newStartDate = newStartDay?.let { ZonedDateTime.parse("2025-02-0${it}T12:00:00Z") }
        val newEndDate = newEndDay?.let { ZonedDateTime.parse("2025-02-${it}T12:00:00Z") }
        hanke.alueet[0].haittaAlkuPvm = newStartDate
        hanke.alueet[0].haittaLoppuPvm = newEndDate
        return Pair(hanke.hankeTunnus, hanke.toModifyRequest())
    }

    private fun assertFeaturePropertiesIsReset(hanke: Hanke, propertiesWanted: Map<String, Any?>) {
        assertThat(hanke.alueet).isNotEmpty()
        hanke.alueet.forEach { alue ->
            val features = alue.geometriat?.featureCollection?.features
            assertThat(features).isNotNull().isNotEmpty()
            features?.forEach { feature ->
                assertThat(feature.properties).isEqualTo(propertiesWanted)
            }
        }
    }

    private fun geometriatCount(): Int? =
        jdbcTemplate.queryForObject("SELECT count(*) from geometriat", Int::class.java)

    private fun hankealueCount(): Int? =
        jdbcTemplate.queryForObject("SELECT count(*) from hankealue", Int::class.java)

    private fun haittojenhallintasuunnitelmaCount(): Int? =
        jdbcTemplate.queryForObject(
            "SELECT count(*) from hankkeen_haittojenhallintasuunnitelma",
            Int::class.java,
        )
}
