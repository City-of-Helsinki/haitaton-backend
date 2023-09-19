package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val USERID = "test-user"

class GdprServiceTest {

    private val applicationService: ApplicationService = mockk(relaxUnitFun = true)
    private val gdprService = GdprService(applicationService)

    @Nested
    inner class DeleteApplications {
        @Test
        fun `Does nothing with an empty list`() {
            gdprService.deleteApplications(listOf(), USERID)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `Deletes all given applications`() {
            val applications = AlluDataFactory.createApplications(4)

            gdprService.deleteApplications(applications, USERID)

            verifyAll {
                applicationService.deleteWithOrphanGeneratedHankeRemoval(1, USERID)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(2, USERID)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(3, USERID)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(4, USERID)
            }
        }
    }
}
