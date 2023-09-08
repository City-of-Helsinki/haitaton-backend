package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.getResource
import jakarta.mail.internet.MimeMessage
import mu.KotlinLogging
import net.pwall.mustache.Template
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.Delimiter
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "haitaton.email")
data class EmailProperties(
    val from: String,
    val baseUrl: String,
    val filter: EmailFilterProperties
)

data class EmailFilterProperties(
    val use: Boolean,
    @Delimiter(";") val allowList: List<String>,
)

data class ApplicationInvitationData(
    val inviterName: String,
    val inviterEmail: String,
    val recipientEmail: String,
    val applicationType: ApplicationType,
    val applicationIdentifier: String,
    val hankeTunnus: String,
    val roleType: ApplicationContactType,
)

data class HankeInvitationData(
    val inviterName: String,
    val inviterEmail: String,
    val recipientEmail: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val invitationToken: String,
)

enum class EmailTemplate(val value: String) {
    CABLE_REPORT_DONE("johtoselvitys-valmis"),
    INVITATION_HANKE("kayttaja-lisatty-hanke"),
    INVITATION_APPLICATION("kayttaja-lisatty-hakemus")
}

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

        sendHybridEmail(to, EmailTemplate.CABLE_REPORT_DONE, templateData)
    }

    fun sendHankeInvitationEmail(data: HankeInvitationData) {
        logger.info { "Sending invitation email for Hanke" }

        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "inviterName" to data.inviterName,
                "inviterEmail" to data.inviterEmail,
                "hankeTunnus" to data.hankeTunnus,
                "hankeNimi" to data.hankeNimi,
                "invitationToken" to data.invitationToken,
            )

        sendHybridEmail(data.recipientEmail, EmailTemplate.INVITATION_HANKE, templateData)
    }

    fun sendApplicationInvitationEmail(data: ApplicationInvitationData) {
        logger.info { "Sending invitation email for application" }

        val applicationTypeText = convertApplicationTypeFinnish(data.applicationType)

        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "inviterName" to data.inviterName,
                "inviterEmail" to data.inviterEmail,
                "applicationType" to applicationTypeText,
                "applicationIdentifier" to data.applicationIdentifier,
                "hankeTunnus" to data.hankeTunnus,
                "recipientRole" to data.roleType.value,
            )

        sendHybridEmail(data.recipientEmail, EmailTemplate.INVITATION_APPLICATION, templateData)
    }

    private fun sendHybridEmail(to: String, context: EmailTemplate, data: Map<String, String>) {
        if (emailConfig.filter.use && emailNotAllowed(to)) {
            logger.info { "Email recipient not allowed, ignoring email." }
            return
        }
        val basePath = "/email/template"
        val textBody = parseTemplate("$basePath/${context.value}.text.mustache", data)
        val htmlBody = parseTemplate("$basePath/${context.value}.html.mustache", data)
        val subject = parseTemplate("$basePath/${context.value}.subject.mustache", data).trimEnd()

        val mimeMessage: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "utf-8")
        helper.setText(textBody, htmlBody)

        helper.setTo(to)
        helper.setSubject(subject)
        helper.setFrom(emailConfig.from)
        mailSender.send(mimeMessage)
    }

    private fun convertApplicationTypeFinnish(type: ApplicationType): String =
        when (type) {
            ApplicationType.CABLE_REPORT -> "johtoselvityshakemuksen"
        }

    private fun emailNotAllowed(email: String) = !emailConfig.filter.allowList.contains(email)

    private fun parseTemplate(path: String, contextObject: Any): String =
        Template.parse(path.getResource().openStream()).processToString(contextObject)
}
