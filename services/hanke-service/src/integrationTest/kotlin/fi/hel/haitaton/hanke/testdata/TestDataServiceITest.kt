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

@SpringBootTest(properties = ["haitaton.testdata.enabled=true"])
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
            for (i in 1..4) {
                val hanke = hankeFactory.saveEntity()
                alluDataFactory.saveApplicationEntity(USERNAME, hanke) { application ->
                    application.alluStatus = ApplicationStatus.values()[i + 4]
                    application.alluid = i
                    application.applicationIdentifier = "JS00$i"
                    application.applicationData =
                        application.applicationData.copy(pendingOnClient = false)
                }

                alluDataFactory.saveApplicationEntity(USERNAME, hanke) { application ->
                    application.alluStatus = null
                    application.alluid = null
                    application.applicationIdentifier = null
                    application.applicationData =
                        application.applicationData.copy(pendingOnClient = true)
                }
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
