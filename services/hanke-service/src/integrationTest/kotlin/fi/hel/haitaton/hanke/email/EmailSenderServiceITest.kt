package fi.hel.haitaton.hanke.email

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.startsWith
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.firstReceivedMessage
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val TEST_EMAIL = "test@test.test"
private const val HAITATON_NO_REPLY = "no-reply@hel.fi"
private const val APPLICATION_IDENTIFIER = "JS2300001"
private const val DEFAULT_INVITER_NAME = "Matti Meikäläinen"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmailSenderServiceITest : DatabaseTest() {

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
                    "Johtoselvitys JS2300001 / Ledningsutredning JS2300001 / Cable report JS2300001"
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
            val (textBody, htmlBody) = getBodiesFromHybridEmail(email)
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

        @Test
        fun `sendHankeInvitationEmail sends email with correct recipient`() {
            emailSenderService.sendHankeInvitationEmail(hankeInvitation())

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendHankeInvitationEmail sends email with sender from properties`() {
            emailSenderService.sendHankeInvitationEmail(hankeInvitation())

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendHankeInvitationEmail sends email with correct subject`() {
            emailSenderService.sendHankeInvitationEmail(hankeInvitation())

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject).isEqualTo("Sinut on lisätty hankkeelle HAI24-1")
        }

        @Test
        fun `sendHankeInvitationEmail sends email with parametrized hybrid body`() {
            val data = hankeInvitation()

            emailSenderService.sendHankeInvitationEmail(data)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = getBodiesFromHybridEmail(email)
            assertThat(textBody).all {
                contains("${data.inviterName} (${data.inviterEmail}) lisäsi sinut")
                contains("hankkeelle ${data.hankeNimi} (${data.hankeTunnus}).")
                contains("http://localhost:3001/${data.invitationToken}")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                contains("$htmlEscapedName (${data.inviterEmail})")
                contains("hankkeelle <b>${data.hankeNimi} (${data.hankeTunnus})</b>.")
                contains("""<a href="http://localhost:3001/${data.invitationToken}">""")
            }
        }
    }

    @Nested
    inner class ApplicationNotification {
        @Test
        fun `sendApplicationNotificationEmail sends email with correct recipient`() {
            emailSenderService.sendApplicationNotificationEmail(applicationNotification())

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(TEST_EMAIL)
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with sender from properties`() {
            emailSenderService.sendApplicationNotificationEmail(applicationNotification())

            val email = greenMail.firstReceivedMessage()
            assertThat(email.from).hasSize(1)
            assertThat(email.from[0].toString()).isEqualTo(HAITATON_NO_REPLY)
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with correct subject`() {
            val data = applicationNotification()
            emailSenderService.sendApplicationNotificationEmail(data)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.subject)
                .isEqualTo("Sinut on lisätty hakemukselle ${data.applicationIdentifier}")
        }

        @Test
        fun `sendApplicationNotificationEmail sends email with parametrized hybrid body`() {
            val data = applicationNotification()

            emailSenderService.sendApplicationNotificationEmail(data)

            val email = greenMail.firstReceivedMessage()
            val (textBody, htmlBody) = getBodiesFromHybridEmail(email)
            assertThat(textBody).all {
                contains("${data.inviterName} (${data.inviterEmail}) on")
                contains("tehnyt johtoselvityshakemuksen (${data.applicationIdentifier})")
                contains("hankkeella ${data.hankeTunnus}")
                contains("rooliin ${data.roleType.value}.")
                contains("Tarkastele hakemusta Haitattomassa: http://localhost:3001")
            }
            assertThat(htmlBody).all {
                val htmlEscapedName = "Matti Meik&auml;l&auml;inen"
                val htmlEscapedRole = "ty&ouml;n suorittaja"
                contains("$htmlEscapedName (${data.inviterEmail})")
                contains("johtoselvityshakemuksen (${data.applicationIdentifier})")
                contains("rooliin $htmlEscapedRole")
                contains("""<a href="http://localhost:3001">""")
            }
        }
    }

    /** Returns a (text body, HTML body) pair. */
    private fun getBodiesFromHybridEmail(email: MimeMessage): Pair<String, String> {
        assertThat(email.content).isInstanceOf(MimeMultipart::class)
        val mp = email.content as MimeMultipart
        assertThat(mp.contentType).startsWith("multipart/mixed")
        assertThat(mp.count).isEqualTo(1)

        assertThat(mp.getBodyPart(0).content).isInstanceOf(MimeMultipart::class)
        val mp2 = mp.getBodyPart(0).content as MimeMultipart
        assertThat(mp2.contentType).startsWith("multipart/related")
        assertThat(mp2.count).isEqualTo(1)

        assertThat(mp2.getBodyPart(0).content).isInstanceOf(MimeMultipart::class)
        val mp3 = mp2.getBodyPart(0).content as MimeMultipart
        assertThat(mp3.contentType).startsWith("multipart/alternative")
        assertThat(mp3.count).isEqualTo(2)

        val bodies =
            if (mp3.getBodyPart(0).contentType.startsWith("text/plain")) {
                    listOf(0, 1)
                } else {
                    listOf(1, 0)
                }
                .map { i -> mp3.getBodyPart(i).content.toString() }
        return Pair(bodies[0], bodies[1])
    }

    private fun hankeInvitation(inviterName: String = DEFAULT_INVITER_NAME) =
        HankeInvitationData(
            inviterName = inviterName,
            inviterEmail = "matti.meikalainen@test.fi",
            recipientEmail = TEST_EMAIL,
            hankeTunnus = "HAI24-1",
            hankeNimi = "Mannerheimintien liikenneuudistus",
            invitationToken = "MgtzRbcPsvoKQamnaSxCnmW7",
        )

    private fun applicationNotification(inviterName: String = DEFAULT_INVITER_NAME) =
        ApplicationNotificationData(
            inviterName = inviterName,
            inviterEmail = "matti.meikalainen@test.fi",
            recipientEmail = TEST_EMAIL,
            applicationType = ApplicationType.CABLE_REPORT,
            applicationIdentifier = APPLICATION_IDENTIFIER,
            hankeTunnus = "HAI24-1",
            roleType = ApplicationContactType.TYON_SUORITTAJA,
        )
}
