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
import fi.hel.haitaton.hanke.firstReceivedMessage
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default", "emailtest")
class EmailSenderServiceITest : DatabaseTest() {

    @JvmField
    @RegisterExtension
    final val greenMail: GreenMailExtension =
        GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())

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
    fun `sendJohtoselvitysCompleteEmail sends email with sender from properties`() {
        emailSenderService.sendJohtoselvitysCompleteEmail(
            "test@test.test",
            "HAI23-001",
            "JS2300001"
        )

        val email = greenMail.firstReceivedMessage()
        assertThat(email.from).hasSize(1)
        assertThat(email.from[0].toString()).isEqualTo("no-reply@hel.fi")
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email with correct subject`() {
        emailSenderService.sendJohtoselvitysCompleteEmail(
            "test@test.test",
            "HAI23-001",
            "JS2300001"
        )

        val email = greenMail.firstReceivedMessage()
        assertThat(email.subject).isEqualTo("Hakemanne johtoselvitys JS2300001 on käsitelty")
    }

    @Test
    fun `sendJohtoselvitysCompleteEmail sends email with parametrized hybrid body`() {
        emailSenderService.sendJohtoselvitysCompleteEmail(
            "test@test.test",
            "HAI23-001",
            "JS2300001"
        )

        val email = greenMail.firstReceivedMessage()
        val (textBody, htmlBody) = getBodiesFromHybridEmail(email)
        assertThat(textBody).all {
            contains("JS2300001")
            contains("http://localhost:3001/fi/hankesalkku/HAI23-001")
        }
        assertThat(htmlBody).all {
            contains("JS2300001")
            contains("""<a href="http://localhost:3001/fi/hankesalkku/HAI23-001">""")
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
}
