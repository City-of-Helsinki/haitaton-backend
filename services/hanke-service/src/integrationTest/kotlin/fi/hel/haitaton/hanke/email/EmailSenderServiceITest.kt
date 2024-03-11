package fi.hel.haitaton.hanke.email

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.firstReceivedMessage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired

private const val TEST_EMAIL = "test@test.test"
private const val HAITATON_NO_REPLY = "no-reply@hel.fi"
private const val APPLICATION_IDENTIFIER = "JS2300001"
private const val INVITER_NAME = "Matti Meikäläinen"

class EmailSenderServiceITest : IntegrationTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @Autowired lateinit var emailSenderService: EmailSenderService

    @Nested
    inner class JohtoSelvitysComplete {
        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with correct recipient`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with sender from properties`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with correct subject`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
            )

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys JS2300001 / Ledningsutredning JS2300001 / Cable report JS2300001"
                )
        }

        @Test
        fun `sendJohtoselvitysCompleteEmail sends email with parametrized hybrid body`() {
            emailSenderService.sendJohtoselvitysCompleteEmail(
                TEST_EMAIL,
                13L,
                APPLICATION_IDENTIFIER
            )

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains(APPLICATION_IDENTIFIER)
                contains("http://localhost:3001/fi/hakemus/13")
                contains("http://localhost:3001/sv/ansokan/13")
                contains("http://localhost:3001/en/application/13")
            }
            // Compress all whitespace into single spaces so that they don't interfere with
            // matching.
            val squashedHtmlBody = htmlBody.replace("\\s+".toRegex(), " ")
            assertThat(squashedHtmlBody).all {
                contains(APPLICATION_IDENTIFIER)
                contains("""<a href="http://localhost:3001/fi/hakemus/13">""")
                contains("""<a href="http://localhost:3001/sv/ansokan/13">""")
                contains("""<a href="http://localhost:3001/en/application/13">""")
            }
        }
    }

    @Nested
    inner class HankeInvitation {
        private val hankeInvitation =
            HankeInvitationData(
                inviterName = INVITER_NAME,
                inviterEmail = "matti.meikalainen@test.fi",
                recipientEmail = TEST_EMAIL,
                hankeTunnus = "HAI24-1",
                hankeNimi = "Mannerheimintien liikenneuudistus",
                invitationToken = "MgtzRbcPsvoKQamnaSxCnmW7",
            )

        @Test
        fun `sendHankeInvitationEmail sends email with correct recipient`() {
            emailSenderService.sendHankeInvitationEmail(hankeInvitation)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendHankeInvitationEmail sends email with sender from properties`() {
            emailSenderService.sendHankeInvitationEmail(hankeInvitation)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendHankeInvitationEmail sends email with correct subject`() {
            emailSenderService.sendHankeInvitationEmail(hankeInvitation)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on lisätty hankkeelle HAI24-1 " +
                        "/ Du har lagts till i projektet HAI24-1 " +
                        "/ You have been added to project HAI24-1"
                )
        }

        @Test
        fun `sendHankeInvitationEmail sends email with parametrized hybrid body`() {
            val data = hankeInvitation

            emailSenderService.sendHankeInvitationEmail(data)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${data.inviterName} (${data.inviterEmail}) lisäsi sinut")
                contains("hankkeelle ${data.hankeNimi} (${data.hankeTunnus}).")
                contains("http://localhost:3001/fi/kutsu?id=${data.invitationToken}")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                contains("$htmlEscapedName (${data.inviterEmail})")
                contains("hankkeelle <b>${data.hankeNimi} (${data.hankeTunnus})</b>.")
                contains("""<a href="http://localhost:3001/fi/kutsu?id=${data.invitationToken}">""")
            }
        }
    }

    @Nested
    inner class ApplicationNotification {
        private val applicationNotification =
            ApplicationNotificationData(
                senderName = INVITER_NAME,
                senderEmail = "matti.meikalainen@test.fi",
                recipientEmail = TEST_EMAIL,
                applicationType = ApplicationType.CABLE_REPORT,
                applicationIdentifier = APPLICATION_IDENTIFIER,
                hankeTunnus = "HAI24-1",
            )

        @Test
        fun `sendApplicationNotificationEmail sends email with correct recipient`() {
            emailSenderService.sendApplicationNotificationEmail(applicationNotification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with sender from properties`() {
            emailSenderService.sendApplicationNotificationEmail(applicationNotification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with correct subject`() {
            emailSenderService.sendApplicationNotificationEmail(applicationNotification)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Sinut on lisätty hakemukselle JS2300001 " +
                        "/ Du har lagts till i ansökan JS2300001 " +
                        "/ You have been added to application JS2300001"
                )
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with parametrized hybrid body`() {
            val data = applicationNotification

            emailSenderService.sendApplicationNotificationEmail(data)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = email.bodies()
            assertThat(textBody).all {
                contains("${data.senderName} (${data.senderEmail}) on")
                contains("tehnyt johtoselvityshakemuksen (${data.applicationIdentifier})")
                contains("hankkeella ${data.hankeTunnus}")
                contains("Tarkastele hakemusta Haitattomassa: http://localhost:3001")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                contains("$htmlEscapedName (${data.senderEmail})")
                contains("johtoselvityshakemuksen (${data.applicationIdentifier})")
                contains("""<a href="http://localhost:3001">""")
            }
        }
    }
}
