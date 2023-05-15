package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.getResource
import javax.mail.internet.MimeMessage
import mu.KotlinLogging
import net.pwall.mustache.Template
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

private const val JOHTOSELVITYS_VALMIS = "johtoselvitys-valmis"

interface EmailSenderService {
    fun sendJohtoselvitysCompleteEmail(to: String, hankeTunnus: String, applicationId: String)
}

@Service
@ConditionalOnProperty(value = ["haitaton.email.filtering"], havingValue = "false")
class EmailSenderServiceImpl(private val emailClient: EmailClient) : EmailSenderService {

    init {
        logger.info { "Initialized EmailSender without recipient filtering. For production." }
    }

    override fun sendJohtoselvitysCompleteEmail(
        to: String,
        hankeTunnus: String,
        applicationId: String
    ) = emailClient.sendHybridEmail(to, hankeTunnus, applicationId)
}

/**
 * Component used to filter sent emails. Will not send the mail if recipient is not in allow list.
 * Can be used e.g. in environments other than production.
 */
@Service
@ConditionalOnProperty(value = ["haitaton.email.filtering"], havingValue = "true")
class EmailSenderServiceFilteredImpl(
    private val emailClient: EmailClient,
    @Value("\${haitaton.email.allow-list}") private val allowListRaw: String,
) : EmailSenderService {

    init {
        logger.info {
            "Initialized EmailSender with allow list recipient filtering. For non-prod environments."
        }
    }

    private val allowList = allowListRaw.split(";").map { it.trim() }

    override fun sendJohtoselvitysCompleteEmail(
        to: String,
        hankeTunnus: String,
        applicationId: String
    ) {
        if (!allowList.contains(to)) {
            logger.info { "Recipient not in allowed addresses, will not send." }
            return
        }
        emailClient.sendHybridEmail(to, hankeTunnus, applicationId)
    }
}

@Component
class EmailClient(
    private val mailSender: JavaMailSender,
    @Value("\${haitaton.email.from}") private val from: String,
    @Value("\${haitaton.email.baseUrl}") private val baseUrl: String,
) {
    fun sendHybridEmail(to: String, hankeTunnus: String, applicationId: String) {
        logger.info { "Sending email for completed johtoselvitys $applicationId" }

        with(TemplateDataProvider) {
            val templateData = createData(baseUrl, hankeTunnus, applicationId)
            val textBody = textBody(JOHTOSELVITYS_VALMIS, templateData)
            val htmlBody = htmlBody(JOHTOSELVITYS_VALMIS, templateData)
            val subject = subject(JOHTOSELVITYS_VALMIS, templateData)

            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "utf-8")
            helper.setText(textBody, htmlBody)

            helper.setTo(to)
            helper.setSubject(subject)
            helper.setFrom(from)
            mailSender.send(mimeMessage)
        }
    }
}

object TemplateDataProvider {
    fun createData(
        baseUrl: String,
        hankeTunnus: String,
        applicationIdentifier: String
    ): Map<String, String> =
        mapOf(
            "baseUrl" to baseUrl,
            "hankeTunnus" to hankeTunnus,
            "applicationIdentifier" to applicationIdentifier,
        )

    fun textBody(template: String, data: Map<String, String>) =
        parseTemplate("/email/template/$template.text.mustache", data)

    fun htmlBody(template: String, data: Map<String, String>) =
        parseTemplate("/email/template/$template.html.mustache", data)

    fun subject(template: String, data: Map<String, String>) =
        parseTemplate("/email/template/$template.subject.mustache", data).trimEnd()

    private fun parseTemplate(path: String, contextObject: Any): String =
        Template.parse(path.getResource().openStream()).processToString(contextObject)
}
