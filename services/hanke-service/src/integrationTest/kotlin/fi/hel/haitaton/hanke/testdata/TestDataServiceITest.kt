package fi.hel.haitaton.hanke.testdata

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNull
import assertk.assertions.prop
import ch.qos.logback.access.tomcat.LogbackValve.DEFAULT_FILENAME
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.paatos.PaatosRepository
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

class TestDataServiceITest : IntegrationTest() {

    @Autowired private lateinit var testDataService: TestDataService
    @Autowired private lateinit var hakemusRepository: HakemusRepository
    @Autowired private lateinit var paatosRepository: PaatosRepository
    @Autowired private lateinit var taydennysRepository: TaydennysRepository
    @Autowired private lateinit var taydennyspyyntoRepository: TaydennyspyyntoRepository
    @Autowired private lateinit var hakemusFactory: HakemusFactory
    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var paatosFactory: PaatosFactory
    @Autowired private lateinit var taydennysFactory: TaydennysFactory
    @Autowired private lateinit var taydennysAttachmentFactory: TaydennysAttachmentFactory
    @Autowired private lateinit var fileClient: MockFileClient

    @Nested
    inner class UnlinkApplicationsFromAllu {
        @Test
        fun `Doesn't throw an exception without applications`() {
            testDataService.unlinkApplicationsFromAllu()
        }

        @Test
        fun `With applications resets their allu fields`() {
            for (i in 1..4) {
                val hanke = hankeFactory.builder(USERNAME).saveEntity()
                hakemusFactory
                    .builder(hanke)
                    .withStatus(status = ApplicationStatus.entries[i + 4], alluId = i)
                    .withPaperReceiver()
                    .save()

                hakemusFactory.builder(hanke).save()
            }

            testDataService.unlinkApplicationsFromAllu()

            val applications = hakemusRepository.findAll()
            assertThat(applications).hasSize(8)
            assertThat(applications).each { application ->
                application.prop(HakemusEntity::alluid).isNull()
                application.prop(HakemusEntity::applicationIdentifier).isNull()
                application.prop(HakemusEntity::alluStatus).isNull()
                application
                    .prop(HakemusEntity::hakemusEntityData)
                    .prop(HakemusEntityData::paperDecisionReceiver)
                    .isNull()
            }
        }

        @Test
        fun `deletes taydennyspyynnot and taydennykset`() {
            taydennysFactory.saveWithHakemus()

            testDataService.unlinkApplicationsFromAllu()

            assertThat(taydennysRepository.findAll()).isEmpty()
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
        }

        @Test
        fun `deletes taydennys attachments from Blob storage`() {
            val taydennys = taydennysFactory.saveWithHakemus()
            taydennysAttachmentFactory.save(taydennys = taydennys).withContent().value

            testDataService.unlinkApplicationsFromAllu()

            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
        }

        @Test
        fun `deletes paatokset`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().withStatus().save()
            paatosFactory.save(hakemus)

            testDataService.unlinkApplicationsFromAllu()

            assertThat(paatosRepository.findAll()).isEmpty()
        }

        @Test
        fun `deletes paatos attachments from Blob storage`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().withStatus().save()
            val paatos = paatosFactory.save(hakemus)
            fileClient.upload(
                Container.PAATOKSET,
                paatos.blobLocation,
                DEFAULT_FILENAME,
                MediaType.APPLICATION_PDF,
                PDF_BYTES,
            )

            testDataService.unlinkApplicationsFromAllu()

            assertThat(fileClient.listBlobs(Container.PAATOKSET)).isEmpty()
        }
    }
}
