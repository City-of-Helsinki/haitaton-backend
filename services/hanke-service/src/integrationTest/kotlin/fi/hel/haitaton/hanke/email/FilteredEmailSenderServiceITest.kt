package fi.hel.haitaton.hanke.email

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.application.ApplicationType.CABLE_REPORT
import fi.hel.haitaton.hanke.firstReceivedMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    properties =
        [
            "haitaton.email.filter.use=true",
            "haitaton.email.filter.allow-list=test@test.test;something@mail.com;*@wildcard.com",
            "haitaton.features.user-management=false"
        ]
)
@ActiveProfiles("test")
class FilteredEmailSenderServiceITest : DatabaseTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Autowired lateinit var emailSenderService: EmailSenderService

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email with allowed recipient`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("test@test.test", 15L, "JS2300001")

        val email = greenMail.firstReceivedMessage()
        assertThat(email.allRecipients).hasSize(1)
        assertThat(email.allRecipients[0].toString()).isEqualTo("test@test.test")
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail blocks send when recipient is not in allow list`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("foo@bar.test", 13L, "JS2300001")

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail blocks send when recipient is close to an allowed email`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("atest@test.test", 13L, "JS2300001")

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail blocks send when dot is replaced`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("test@test-test", 13L, "JS2300001")

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email when email matches wildcard`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("test@wildcard.com", 15L, "JS2300001")

        val email = greenMail.firstReceivedMessage()
        assertThat(email.allRecipients).hasSize(1)
        assertThat(email.allRecipients[0].toString()).isEqualTo("test@wildcard.com")
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email when email with suffix matches wildcard`() {
        emailSenderService.sendJohtoselvitysCompleteEmail(
            "test+suffix@wildcard.com",
            15L,
            "JS2300001"
        )

        val email = greenMail.firstReceivedMessage()
        assertThat(email.allRecipients).hasSize(1)
        assertThat(email.allRecipients[0].toString()).isEqualTo("test+suffix@wildcard.com")
    }

    @Test
    fun `sendApplicationNotificationEmail does not send when user management is not enabled`() {
        emailSenderService.sendApplicationNotificationEmail(
            ApplicationNotificationData(
                senderName = "Kalle Kutsuja",
                senderEmail = "kalle.kutsuja@mail.com",
                recipientEmail = "test@test.test",
                applicationType = CABLE_REPORT,
                applicationIdentifier = "JS002",
                hankeTunnus = "HAI24-1",
            )
        )

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }
}
