package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.getResourceAsText
import javax.mail.internet.MimeMessage
import mu.KotlinLogging
import net.pwall.mustache.Template
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class EmailSenderService(
    private val mailSender: JavaMailSender,
    @Value("\${haitaton.email.enabled}") private val enabled: Boolean,
    @Value("\${haitaton.email.from}") private val from: String,
    @Value("\${haitaton.email.baseUrl}") private val baseUrl: String,
) {

    /* Uncomment to send test email at startup. Will be removed before merge to master.
    @EventListener(ApplicationReadyEvent::class)
    fun test() {
        sendJohtoselvitysCompleteEmail("vastaanottaja@ref.test", "HAI23-12", "JS2300013")
    }
    */

    private fun sendHybridEmail(to: String, template: String, templateData: Map<String, String>) {
        if (!enabled) {
            logger.info { "Email sending not enabled, ignoring email" }
            return
        }
        val textBody =
            Template.parse("/email/template/$template.text.mustache".getResourceAsText())
                .processToString(templateData)
        val htmlBody =
            Template.parse("/email/template/$template.html.mustache".getResourceAsText())
                .processToString(templateData)
        val subject =
            Template.parse("/email/template/$template.subject.mustache".getResourceAsText())
                .processToString(templateData)
                .trimEnd()

        val mimeMessage: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "utf-8")
        helper.setText(textBody, htmlBody)

        helper.setTo(to)
        helper.setSubject(subject)
        helper.setFrom(from)
        mailSender.send(mimeMessage)
    }

    fun sendJohtoselvitysCompleteEmail(
        to: String,
        hankeTunnus: String,
        applicationIdentifier: String
    ) {
        logger.info { "Sending email for completed johtoselvitys $applicationIdentifier" }
        val templateData =
            mapOf(
                "baseUrl" to baseUrl,
                "hankeTunnus" to hankeTunnus,
                "applicationIdentifier" to applicationIdentifier,
            )
        sendHybridEmail(to, "johtoselvitys-valmis", templateData)
    }
}
