package fi.hel.haitaton.hanke.email

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.application.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.application.ApplicationType.CABLE_REPORT
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
            "haitaton.email.filter.allow-list=test@test.test;something@mail.com",
            "haitaton.features.user-management=false"
        ]
)
@ActiveProfiles("test")
class EmailSenderServicePropertiesITest : DatabaseTest() {

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
    fun `sendJohtoselvitysCompleteEmail when recipient not in allow list does not send`() {
        emailSenderService.sendJohtoselvitysCompleteEmail("foo@bar.test", 13L, "JS2300001")

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }

    @Test
    fun `sendApplicationNotificationEmail when user management not enabled does not send`() {
        emailSenderService.sendApplicationNotificationEmail(
            ApplicationNotificationData(
                senderName = "Kalle Kutsuja",
                senderEmail = "kalle.kutsuja@mail.com",
                recipientEmail = "matti.meikalainen@mail.com",
                applicationType = CABLE_REPORT,
                applicationIdentifier = "JS002",
                hankeTunnus = "HAI24-1",
                roleType = TYON_SUORITTAJA,
            )
        )

        assertThat(greenMail.receivedMessages.size).isEqualTo(0)
    }
}
