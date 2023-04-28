package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.getResource
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

    fun sendJohtoselvitysCompleteEmail(
        to: String,
        hankeTunnus: String,
        applicationIdentifier: String,
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

    private fun sendHybridEmail(to: String, template: String, templateData: Map<String, String>) {
        if (!enabled) {
            logger.info { "Email sending not enabled, ignoring email" }
            return
        }
        val textBody = parseTemplate("/email/template/$template.text.mustache", templateData)
        val htmlBody = parseTemplate("/email/template/$template.html.mustache", templateData)
        val subject =
            parseTemplate("/email/template/$template.subject.mustache", templateData).trimEnd()

        val mimeMessage: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "utf-8")
        helper.setText(textBody, htmlBody)

        helper.setTo(to)
        helper.setSubject(subject)
        helper.setFrom(from)
        mailSender.send(mimeMessage)
    }

    private fun parseTemplate(path: String, contextObject: Any): String =
        Template.parse(path.getResource().openStream()).processToString(contextObject)
}
