package fi.hel.haitaton.hanke.gdpr

import assertk.assertFailure
import fi.hel.haitaton.hanke.application.ApplicationDeletionResultDto
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OldGdprServiceTest {

    private val applicationService: ApplicationService = mockk(relaxUnitFun = true)
    private val gdprService = OldGdprService(applicationService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(applicationService)
    }

    @Nested
    inner class DeleteApplications {
        @Test
        fun `Does nothing with an empty list`() {
            every { applicationService.getAllApplicationsCreatedByUser(USERNAME) } returns listOf()

            gdprService.deleteInfo(USERNAME)

            verifySequence { applicationService.getAllApplicationsCreatedByUser(USERNAME) }
        }

        @Test
        fun `Deletes all user's applications`() {
            val applications = ApplicationFactory.createApplications(4)
            every { applicationService.getAllApplicationsCreatedByUser(USERNAME) } returns
                applications
            every { applicationService.isStillPending(null, null) } returns true
            every {
                applicationService.deleteWithOrphanGeneratedHankeRemoval(any(), USERNAME)
            } returns ApplicationDeletionResultDto(hankeDeleted = false)

            gdprService.deleteInfo(USERNAME)

            verifySequence {
                applicationService.getAllApplicationsCreatedByUser(USERNAME)
                applicationService.isStillPending(null, null)
                applicationService.isStillPending(null, null)
                applicationService.isStillPending(null, null)
                applicationService.isStillPending(null, null)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(1, USERNAME)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(2, USERNAME)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(3, USERNAME)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(4, USERNAME)
            }
        }

        @Test
        fun `Throws exception if any applications are no longer pending`() {
            val applications = ApplicationFactory.createApplications(4)
            every { applicationService.getAllApplicationsCreatedByUser(USERNAME) } returns
                applications
            every { applicationService.isStillPending(null, null) } returns false

            assertFailure { gdprService.deleteInfo(USERNAME) }

            verifySequence {
                applicationService.getAllApplicationsCreatedByUser(USERNAME)
                applicationService.isStillPending(null, null)
                applicationService.isStillPending(null, null)
                applicationService.isStillPending(null, null)
                applicationService.isStillPending(null, null)
            }
        }
    }
}
