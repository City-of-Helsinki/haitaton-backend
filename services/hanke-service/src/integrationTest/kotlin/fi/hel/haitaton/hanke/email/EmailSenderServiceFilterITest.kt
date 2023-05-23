package fi.hel.haitaton.hanke.email

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.firstReceivedMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "haitaton.email.filter.use=true",
            "haitaton.email.filter.allow-list=test@test.test;something@mail.com"
        ]
)
@ActiveProfiles("default", "emailtest")
class EmailSenderServiceFilterITest : DatabaseTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Autowired lateinit var emailSenderService: EmailSenderService

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email with correct recipient`() {
        emailSenderService.sendJohtoselvitysCompleteEmail(
            "test@test.test",
            "HAI23-001",
            "JS2300001"
        )

        val email = greenMail.firstReceivedMessage()
        assertThat(email.allRecipients).hasSize(1)
        assertThat(email.allRecipients[0].toString()).isEqualTo("test@test.test")
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail when recipient not in allow list does not send`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("foo@bar.test", "HAI23-001", "JS2300001")

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }
}
