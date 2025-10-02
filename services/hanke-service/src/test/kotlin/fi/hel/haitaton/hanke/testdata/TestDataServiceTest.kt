package fi.hel.haitaton.hanke.testdata

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val SESSION_ID = "session-123"

class TestDataServiceTest {

    private val randomHankeGenerator = mockk<RandomHankeGenerator>()
    private val testDataService =
        TestDataService(
            hakemusRepository = mockk(),
            taydennyspyyntoRepository = mockk(),
            taydennysAttachmentRepository = mockk(),
            paatosRepository = mockk(),
            muutosilmoitusRepository = mockk(),
            muutosilmoitusAttachmentRepository = mockk(),
            attachmentContentService = mockk(),
            fileClient = mockk(),
            alluUpdateService = mockk(),
            randomHankeGenerator = randomHankeGenerator,
            userSessionRepository = mockk(),
        )

    @Test
    fun `calls randomHankeGenerator for each count`() {
        every { randomHankeGenerator.createRandomHanke(any()) } returns Unit

        val result = testDataService.createRandomPublicHanke(3)

        assertEquals(3, result)
        verify(exactly = 3) { randomHankeGenerator.createRandomHanke(any()) }
        verify { randomHankeGenerator.createRandomHanke(0) }
        verify { randomHankeGenerator.createRandomHanke(1) }
        verify { randomHankeGenerator.createRandomHanke(2) }
    }

    @Test
    fun `returns zero when count is zero`() {
        val result = testDataService.createRandomPublicHanke(0)

        assertEquals(0, result)
        verify(exactly = 0) { randomHankeGenerator.createRandomHanke(any()) }
    }

    @Test
    fun `returns correct count when creating one hanke`() {
        every { randomHankeGenerator.createRandomHanke(any()) } returns Unit

        val result = testDataService.createRandomPublicHanke(1)

        assertEquals(1, result)
        verify(exactly = 1) { randomHankeGenerator.createRandomHanke(0) }
    }

    @Test
    fun `passes correct index to each call`() {
        every { randomHankeGenerator.createRandomHanke(any()) } returns Unit

        testDataService.createRandomPublicHanke(5)

        verify { randomHankeGenerator.createRandomHanke(0) }
        verify { randomHankeGenerator.createRandomHanke(1) }
        verify { randomHankeGenerator.createRandomHanke(2) }
        verify { randomHankeGenerator.createRandomHanke(3) }
        verify { randomHankeGenerator.createRandomHanke(4) }
    }

    @Test
    fun `terminateUserSession returns true when session terminated`() {
        val userSessionRepository = mockk<fi.hel.haitaton.hanke.security.UserSessionRepository>()
        every { userSessionRepository.terminateBySessionId(SESSION_ID) } returns 1

        val service =
            TestDataService(
                hakemusRepository = mockk(),
                taydennyspyyntoRepository = mockk(),
                taydennysAttachmentRepository = mockk(),
                paatosRepository = mockk(),
                muutosilmoitusRepository = mockk(),
                muutosilmoitusAttachmentRepository = mockk(),
                attachmentContentService = mockk(),
                fileClient = mockk(),
                alluUpdateService = mockk(),
                randomHankeGenerator = mockk(),
                userSessionRepository = userSessionRepository,
            )

        val result = service.terminateUserSession(SESSION_ID)

        assertEquals(true, result)
        verify { userSessionRepository.terminateBySessionId(SESSION_ID) }
    }

    @Test
    fun `terminateUserSession returns false when session not found`() {
        val userSessionRepository = mockk<fi.hel.haitaton.hanke.security.UserSessionRepository>()
        every { userSessionRepository.terminateBySessionId(SESSION_ID) } returns 0

        val service =
            TestDataService(
                hakemusRepository = mockk(),
                taydennyspyyntoRepository = mockk(),
                taydennysAttachmentRepository = mockk(),
                paatosRepository = mockk(),
                muutosilmoitusRepository = mockk(),
                muutosilmoitusAttachmentRepository = mockk(),
                attachmentContentService = mockk(),
                fileClient = mockk(),
                alluUpdateService = mockk(),
                randomHankeGenerator = mockk(),
                userSessionRepository = userSessionRepository,
            )

        val result = service.terminateUserSession(SESSION_ID)

        assertEquals(false, result)
        verify { userSessionRepository.terminateBySessionId(SESSION_ID) }
    }
}
