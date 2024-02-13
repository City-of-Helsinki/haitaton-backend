package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.application.ApplicationDeletionResultDto
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val USERID = "test-user"

class OldGdprServiceTest {

    private val applicationService: ApplicationService = mockk(relaxUnitFun = true)
    private val gdprService = OldGdprService(applicationService)

    @Nested
    inner class DeleteApplications {
        @Test
        fun `Does nothing with an empty list`() {
            gdprService.deleteApplications(listOf(), USERID)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `Deletes all given applications`() {
            val applications = ApplicationFactory.createApplications(4)
            every {
                applicationService.deleteWithOrphanGeneratedHankeRemoval(any(), USERID)
            } returns ApplicationDeletionResultDto(hankeDeleted = false)

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
