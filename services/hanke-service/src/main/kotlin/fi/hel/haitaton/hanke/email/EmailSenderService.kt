package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.getResource
import fi.hel.haitaton.hanke.getResourceAsText
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
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
) {
    val filterRegexes: List<Regex> by lazy {
        filter.allowList.map { EmailSenderService.expandAsterisks(it).toRegex() }
    }
}

data class EmailFilterProperties(
    val use: Boolean,
    @Delimiter(";") val allowList: List<String>,
)

data class ApplicationNotificationData(
    val senderName: String,
    val senderEmail: String,
    val recipientEmail: String,
    val applicationType: ApplicationType,
    val hankeTunnus: String,
    val hankeNimi: String,
)

data class HankeInvitationData(
    val inviterName: String,
    val inviterEmail: String,
    val recipientEmail: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val invitationToken: String,
)

data class AccessRightsUpdateNotificationData(
    val recipientEmail: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val updatedByName: String,
    val updatedByEmail: String,
    val newAccessRights: Kayttooikeustaso,
)

data class RemovalFromHankeNotificationData(
    val recipientEmail: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val deletedByName: String,
    val deletedByEmail: String,
)

data class Translations(val fi: String, val sv: String, val en: String)

enum class EmailTemplate(val value: String) {
    CABLE_REPORT_DONE("johtoselvitys-valmis"),
    INVITATION_HANKE("kayttaja-lisatty-hanke"),
    APPLICATION_NOTIFICATION("kayttaja-lisatty-hakemus"),
    ACCESS_RIGHTS_UPDATE_NOTIFICATION("kayttooikeustason-muutos"),
    REMOVAL_FROM_HANKE_NOTIFICATION("kayttaja-poistettu-hanke"),
}

@Service
class EmailSenderService(
    private val mailSender: JavaMailSender,
    private val emailConfig: EmailProperties,
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
        logger.info { "Sending notification email for application" }

        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "senderName" to data.senderName,
                "senderEmail" to data.senderEmail,
                "applicationType" to data.applicationType.translations(),
                "hankeTunnus" to data.hankeTunnus,
                "hankeNimi" to data.hankeNimi,
                "signatures" to signatures(),
            )

        sendHybridEmail(data.recipientEmail, EmailTemplate.APPLICATION_NOTIFICATION, templateData)
    }

    fun sendAccessRightsUpdateNotificationEmail(data: AccessRightsUpdateNotificationData) {
        logger.info { "Sending notification email for access rights update" }

        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "recipientEmail" to data.recipientEmail,
                "hankeTunnus" to data.hankeTunnus,
                "hankeNimi" to data.hankeNimi,
                "updatedByName" to data.updatedByName,
                "updatedByEmail" to data.updatedByEmail,
                "newAccessRights" to data.newAccessRights.translations(),
                "signatures" to signatures(),
            )

        sendHybridEmail(
            data.recipientEmail,
            EmailTemplate.ACCESS_RIGHTS_UPDATE_NOTIFICATION,
            templateData
        )
    }

    fun sendRemovalFromHankeNotificationEmail(data: RemovalFromHankeNotificationData) {
        logger.info { "Sending notification email for removal from hanke" }

        val templateData =
            mapOf(
                "baseUrl" to emailConfig.baseUrl,
                "recipientEmail" to data.recipientEmail,
                "hankeTunnus" to data.hankeTunnus,
                "hankeNimi" to data.hankeNimi,
                "deletedByName" to data.deletedByName,
                "deletedByEmail" to data.deletedByEmail,
                "signatures" to signatures(),
            )

        sendHybridEmail(
            data.recipientEmail,
            EmailTemplate.REMOVAL_FROM_HANKE_NOTIFICATION,
            templateData
        )
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
        !emailConfig.filterRegexes.any { it.matches(email) }

    private fun parseTemplate(path: String, contextObject: Any): String =
        Template.parse(path.getResource().openStream()).processToString(contextObject)

    companion object {
        fun ApplicationType.translations() =
            when (this) {
                ApplicationType.CABLE_REPORT ->
                    Translations(
                        fi = "johtoselvityshakemusta",
                        sv = "ledningsutredning",
                        en = "a cable report application",
                    )
                ApplicationType.EXCAVATION_NOTIFICATION ->
                    Translations(
                        fi = "kaivuilmoitusta",
                        sv = "grävningsanmälan",
                        en = "excavating declaration",
                    )
            }

        fun Kayttooikeustaso.translations() =
            when (this) {
                Kayttooikeustaso.KAIKKI_OIKEUDET ->
                    Translations(
                        fi = "Kaikki oikeudet",
                        sv = "Alla rättigheter",
                        en = "All rights",
                    )
                Kayttooikeustaso.KAIKKIEN_MUOKKAUS ->
                    Translations(
                        fi = "Hankkeen ja hakemusten muokkaus",
                        sv = "Ändringar i projekt och ansökningar",
                        en = "Project and application editing",
                    )
                Kayttooikeustaso.HANKEMUOKKAUS ->
                    Translations(
                        fi = "Hankemuokkaus",
                        sv = "Ändring i projekt",
                        en = "Project editing",
                    )
                Kayttooikeustaso.HAKEMUSASIOINTI ->
                    Translations(
                        fi = "Hakemusasiointi",
                        sv = "Ansökningsärende",
                        en = "Application services",
                    )
                Kayttooikeustaso.KATSELUOIKEUS ->
                    Translations(
                        fi = "Katseluoikeus",
                        sv = "Visningsrätt",
                        en = "Viewing right",
                    )
            }
        /**
         * Expand the asterisks in the given pattern to `.*` and surround the literal parts with
         * regex literal flags `\Q` and `\E`.
         */
        fun expandAsterisks(pattern: String): String =
            pattern.split('*').joinToString(".*") { "\\Q$it\\E" }.replace("\\Q\\E", "")
    }
}
