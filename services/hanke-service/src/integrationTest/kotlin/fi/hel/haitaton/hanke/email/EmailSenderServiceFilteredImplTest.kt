package fi.hel.haitaton.hanke.email

import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import io.mockk.Called
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["haitaton.email.filtering=true"]
)
@ActiveProfiles("default", "emailtest")
class EmailSenderServiceFilteredImplTest : DatabaseTest() {

    @Qualifier("emailSenderServiceFilteredImpl")
    @Autowired
    private lateinit var emailSenderService: EmailSenderService

    @MockkBean private lateinit var emailClient: EmailClient

    @Test
    fun sendJohtoselvitysCompleteEmail() {
        emailSenderService.sendJohtoselvitysCompleteEmail(
            "test@test.test",
            "HAI23-001",
            "JS2300001"
        )

        verify { emailClient wasNot Called }
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email with correct recipient`() {
        val to = "haitaton@test.example"
        val hankeTunnus = "HAI23-001"
        val applicationId = "JS2300001"
        justRun { emailClient.sendHybridEmail(to, hankeTunnus, applicationId) }

        emailSenderService.sendJohtoselvitysCompleteEmail(to, hankeTunnus, applicationId)

        verify { emailClient.sendHybridEmail(to, hankeTunnus, applicationId) }
    }
}
