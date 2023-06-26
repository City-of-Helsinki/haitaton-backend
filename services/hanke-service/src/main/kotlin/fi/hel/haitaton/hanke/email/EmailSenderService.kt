package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.getResource
import javax.mail.internet.MimeMessage
import mu.KotlinLogging
import net.pwall.mustache.Template
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.convert.Delimiter
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "haitaton.email")
@ConstructorBinding
data class EmailProperties(
    val from: String,
    val baseUrl: String,
    val filter: EmailFilterProperties
)

data class EmailFilterProperties(
    val use: Boolean,
    @Delimiter(";") val allowList: List<String>,
)

@Service
class EmailSenderService(
    private val mailSender: JavaMailSender,
    private val emailConfig: EmailProperties,
) {

    fun sendJohtoselvitysCompleteEmail(
        to: String,
        applicationId: Long?,
        applicationIdentifier: String,
    ) {
        logger.info { "Sending email for completed johtoselvitys $applicationIdentifier" }
        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "applicationId" to applicationId.toString(),
                "applicationIdentifier" to applicationIdentifier,
            )
        sendHybridEmail(to, "johtoselvitys-valmis", templateData)
    }

    private fun sendHybridEmail(to: String, template: String, templateData: Map<String, String>) {
        if (emailConfig.filter.use && emailNotAllowed(to)) {
            logger.info { "Email recipient not allowed, ignoring email." }
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
        helper.setFrom(emailConfig.from)
        mailSender.send(mimeMessage)
    }

    private fun emailNotAllowed(email: String) = !emailConfig.filter.allowList.contains(email)

    private fun parseTemplate(path: String, contextObject: Any): String =
        Template.parse(path.getResource().openStream()).processToString(contextObject)
}
