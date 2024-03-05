package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.getResource
import fi.hel.haitaton.hanke.getResourceAsText
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

data class ApplicationNotificationData(
    val senderName: String,
    val senderEmail: String,
    val recipientEmail: String,
    val applicationType: ApplicationType,
    val applicationIdentifier: String,
    val hankeTunnus: String,
)

data class HankeInvitationData(
    val inviterName: String,
    val inviterEmail: String,
    val recipientEmail: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val invitationToken: String,
)

data class Translations(val fi: String, val sv: String, val en: String)

enum class EmailTemplate(val value: String) {
    CABLE_REPORT_DONE("johtoselvitys-valmis"),
    INVITATION_HANKE("kayttaja-lisatty-hanke"),
    APPLICATION_NOTIFICATION("kayttaja-lisatty-hakemus")
}

@Service
class EmailSenderService(
    private val mailSender: JavaMailSender,
    private val emailConfig: EmailProperties,
    private val featureFlags: FeatureFlags,
) {
    val templatePath = "/email/template"

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
                "signatures" to signatures(),
            )

        sendHybridEmail(data.recipientEmail, EmailTemplate.INVITATION_HANKE, templateData)
    }

    fun sendApplicationNotificationEmail(data: ApplicationNotificationData) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }

        logger.info { "Sending notification email for application" }

        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "senderName" to data.senderName,
                "senderEmail" to data.senderEmail,
                "applicationType" to data.applicationType.translations(),
                "applicationIdentifier" to data.applicationIdentifier,
                "hankeTunnus" to data.hankeTunnus,
                "signatures" to signatures(),
            )

        sendHybridEmail(data.recipientEmail, EmailTemplate.APPLICATION_NOTIFICATION, templateData)
    }

    private fun sendHybridEmail(to: String, template: EmailTemplate, data: Map<String, Any>) {
        if (emailConfig.filter.use && emailNotAllowed(to)) {
            logger.info { "Email recipient not allowed, ignoring email." }
            return
        }
        val textBody = parseTemplate("$templatePath/${template.value}.text.mustache", data)
        val htmlBody = parseTemplate("$templatePath/${template.value}.html.mustache", data)
        val subject =
            parseTemplate("$templatePath/${template.value}.subject.mustache", data).trimEnd()

        val mimeMessage: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "utf-8")
        helper.setText(textBody, htmlBody)

        helper.setTo(to)
        helper.setSubject(subject)
        helper.setFrom(emailConfig.from)
        mailSender.send(mimeMessage)
    }

    private fun signatures() =
        Translations(
            "$templatePath/common/signature-fi.mustache".getResourceAsText(),
            "$templatePath/common/signature-sv.mustache".getResourceAsText(),
            "$templatePath/common/signature-en.mustache".getResourceAsText(),
        )

    private fun emailNotAllowed(email: String) =
        !emailConfig.filter.allowList.any { it.toRegex().matches(email) }

    private fun parseTemplate(path: String, contextObject: Any): String =
        Template.parse(path.getResource().openStream()).processToString(contextObject)

    companion object {
        fun ApplicationType.translations() =
            when (this) {
                ApplicationType.CABLE_REPORT ->
                    Translations(
                        fi = "johtoselvityshakemuksen",
                        sv = "ledningsutredning",
                        en = "a cable report application",
                    )
            }
    }
}
