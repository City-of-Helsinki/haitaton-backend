package fi.hel.haitaton.hanke.testdata

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isNull
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val USERNAME = "testUser"

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TestDataServiceITest : DatabaseTest() {

    @Autowired private lateinit var testDataService: TestDataService
    @Autowired private lateinit var applicationRepository: ApplicationRepository
    @Autowired private lateinit var alluDataFactory: AlluDataFactory
    @Autowired private lateinit var hankeFactory: HankeFactory

    @Nested
    inner class UnlinkApplicationsFromAllu {
        @Test
        fun `Doesn't throw an exception without applications`() {
            testDataService.unlinkApplicationsFromAllu()
        }

        @Test
        fun `With applications resets their allu fields`() {
            val applicationData = AlluDataFactory.createCableReportApplicationData()
            for (i in 1..4) {
                val hanke = hankeFactory.saveEntity()
                alluDataFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = hanke,
                    alluStatus = ApplicationStatus.entries[i + 4],
                    alluId = i,
                    applicationIdentifier = "JS00$i",
                    applicationData = applicationData.copy(pendingOnClient = false),
                )

                alluDataFactory.saveApplicationEntity(
                    USERNAME,
                    hanke,
                    alluStatus = null,
                    alluId = null,
                    applicationIdentifier = null,
                    applicationData = applicationData.copy(pendingOnClient = true),
                )
            }

            testDataService.unlinkApplicationsFromAllu()

            val applications = applicationRepository.findAll()
            assertThat(applications).hasSize(8)
            assertThat(applications).each { application ->
                application.transform { it.alluid }.isNull()
                application.transform { it.applicationIdentifier }.isNull()
                application.transform { it.alluStatus }.isNull()
                application.transform { it.applicationData.pendingOnClient }.isTrue()
            }
        }
    }
}
