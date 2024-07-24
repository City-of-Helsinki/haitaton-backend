package fi.hel.haitaton.hanke.paatos

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import assertk.assertions.single
import assertk.assertions.startsWith
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TestFile
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.paatos.PaatosTila.KORVATTU
import fi.hel.haitaton.hanke.paatos.PaatosTila.NYKYINEN
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.PAATOS
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.TOIMINNALLINEN_KUNTO
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.TYO_VALMIS
import fi.hel.haitaton.hanke.test.AlluException
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

class PaatosServiceITest(
    @Autowired private val paatosService: PaatosService,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val paatosFactory: PaatosFactory,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val paatosRepository: PaatosRepository,
) : IntegrationTest() {

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluClient)
    }

    @Nested
    inner class FindByHakemusId {

        @Test
        fun `returns empty list when there are no decisions`() {
            val result = paatosService.findByHakemusId(100L)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns list of all decisions when they exist`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .save()
            paatosFactory.save(hakemus, "KP2400001", PAATOS, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-1", PAATOS, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-2", PAATOS, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-3", PAATOS, NYKYINEN)
            paatosFactory.save(hakemus, "KP2400001-1", TOIMINNALLINEN_KUNTO, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-2", TOIMINNALLINEN_KUNTO, KORVATTU)
            paatosFactory.save(hakemus, "KP2400001-3", TOIMINNALLINEN_KUNTO, NYKYINEN)
            paatosFactory.save(hakemus, "KP2400001-3", TYO_VALMIS, NYKYINEN)

            val result = paatosService.findByHakemusId(hakemus.id)

            assertThat(result).hasSize(8)
            assertThat(result)
                .extracting { t -> listOf(t.hakemustunnus, t.tyyppi.toString(), t.tila.toString()) }
                .containsExactly(
                    listOf("KP2400001", PAATOS.toString(), KORVATTU.toString()),
                    listOf("KP2400001-1", PAATOS.toString(), KORVATTU.toString()),
                    listOf("KP2400001-2", PAATOS.toString(), KORVATTU.toString()),
                    listOf("KP2400001-3", PAATOS.toString(), NYKYINEN.toString()),
                    listOf("KP2400001-1", TOIMINNALLINEN_KUNTO.toString(), KORVATTU.toString()),
                    listOf("KP2400001-2", TOIMINNALLINEN_KUNTO.toString(), KORVATTU.toString()),
                    listOf("KP2400001-3", TOIMINNALLINEN_KUNTO.toString(), NYKYINEN.toString()),
                    listOf("KP2400001-3", TYO_VALMIS.toString(), NYKYINEN.toString()),
                )
        }

        @Test
        fun `returns decisions only for the requested application when they exist for several`() {
            val hakemus1 =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(alluId = 11)
                    .withName("First")
                    .save()
            val hakemus2 =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(alluId = 12)
                    .withName("Second")
                    .save()
            val hakemus3 =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(alluId = 13)
                    .withName("Third")
                    .save()
            paatosFactory.save(hakemus1)
            paatosFactory.save(hakemus2)
            paatosFactory.save(hakemus3)

            val result = paatosService.findByHakemusId(hakemus2.id)

            assertThat(result).extracting { it.hakemusId }.containsExactly(hakemus2.id)
        }
    }

    @Nested
    inner class SaveKaivuilmoituksenPaatos {
        private val eventTime = ZonedDateTime.parse("2024-07-21T15:23:12Z")
        private val alluId = 631
        private val hakemustunnus = "KP2400414"
        private val nameFromAllu = "Name from Allu"
        private val startDateFromAllu = ZonedDateTime.parse("2024-07-24T15:23:12Z")
        private val endDateFromAllu = ZonedDateTime.parse("2024-07-25T15:23:12Z")
        private val event =
            ApplicationStatusEvent(
                eventTime = eventTime,
                applicationIdentifier = hakemustunnus,
                newStatus = ApplicationStatus.DECISION,
                targetStatus = null,
            )

        private fun setupAlluMocks() {
            every { alluClient.getDecisionPdf(alluId) } returns PDF_BYTES
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(
                    id = alluId,
                    name = nameFromAllu,
                    startTime = startDateFromAllu,
                    endTime = endDateFromAllu,
                )
        }

        @Test
        fun `saves decision PDF to blob storage`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION, alluId, hakemustunnus)
                    .saveEntity()
            setupAlluMocks()

            paatosService.saveKaivuilmoituksenPaatos(hakemus, event)

            val decisions = fileClient.listBlobs(Container.PAATOKSET)
            assertThat(decisions).single().all {
                prop(TestFile::path).startsWith("${hakemus.id}/")
                prop(TestFile::contentType).isEqualTo(MediaType.APPLICATION_PDF)
                prop(TestFile::contentLength).isEqualTo(PDF_BYTES.size)
                prop(TestFile::contentDisposition)
                    .isEqualTo("attachment; filename*=UTF-8''$hakemustunnus-paatos.pdf")
                prop(TestFile::content).transform { it.toBytes() }.isEqualTo(PDF_BYTES)
            }
            verifySequence {
                alluClient.getDecisionPdf(alluId)
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `writes the decision data to database`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION, alluId, hakemustunnus)
                    .saveEntity()
            setupAlluMocks()

            paatosService.saveKaivuilmoituksenPaatos(hakemus, event)

            assertThat(paatosRepository.findAll()).single().all {
                prop(PaatosEntity::hakemusId).isEqualTo(hakemus.id)
                prop(PaatosEntity::hakemustunnus).isEqualTo(hakemustunnus)
                prop(PaatosEntity::tyyppi).isEqualTo(PAATOS)
                prop(PaatosEntity::tila).isEqualTo(NYKYINEN)
            }
            verifySequence {
                alluClient.getDecisionPdf(alluId)
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `reads name and dates from Allu`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION, alluId, hakemustunnus)
                    .withName("Old name")
                    .withStartTime(ZonedDateTime.parse("2023-07-24T15:23:12Z"))
                    .withEndTime(ZonedDateTime.parse("2023-07-24T15:23:12Z"))
                    .saveEntity()
            setupAlluMocks()

            paatosService.saveKaivuilmoituksenPaatos(hakemus, event)

            assertThat(paatosRepository.findAll()).single().all {
                prop(PaatosEntity::nimi).isEqualTo(nameFromAllu)
                prop(PaatosEntity::alkupaiva).isEqualTo(startDateFromAllu.toLocalDate())
                prop(PaatosEntity::loppupaiva).isEqualTo(endDateFromAllu.toLocalDate())
            }
            verifySequence {
                alluClient.getDecisionPdf(alluId)
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws exception when getting the PDF from Allu throws an exception`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION, alluId, hakemustunnus)
                    .saveEntity()
            every { alluClient.getDecisionPdf(alluId) } throws AlluException()

            val failure = assertFailure { paatosService.saveKaivuilmoituksenPaatos(hakemus, event) }

            failure.isInstanceOf(AlluException::class.java)
            assertThat(paatosRepository.findAll()).isEmpty()
            verifySequence { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `doesn't save the decision when getting application information fails`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION, alluId, hakemustunnus)
                    .saveEntity()
            every { alluClient.getDecisionPdf(alluId) } returns PDF_BYTES
            every { alluClient.getApplicationInformation(alluId) } throws AlluException()

            val failure = assertFailure { paatosService.saveKaivuilmoituksenPaatos(hakemus, event) }

            failure.isInstanceOf(AlluException::class.java)
            assertThat(paatosRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.PAATOKSET)).isEmpty()
            verifySequence {
                alluClient.getDecisionPdf(alluId)
                alluClient.getApplicationInformation(alluId)
            }
        }
    }
}
