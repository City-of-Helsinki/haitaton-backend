package fi.hel.haitaton.hanke.email

import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso

sealed interface EmailEvent {
    val to: String
}

data class JohtoselvitysCompleteEmail(
    override val to: String,
    val applicationId: Long?,
    val applicationIdentifier: String,
) : EmailEvent

data class KaivuilmoitusDecisionEmail(
    override val to: String,
    val applicationId: Long?,
    val applicationIdentifier: String,
) : EmailEvent

data class ApplicationNotificationEmail(
    val senderName: String,
    val senderEmail: String,
    override val to: String,
    val applicationType: ApplicationType,
    val hankeTunnus: String,
    val hankeNimi: String,
) : EmailEvent

data class HankeInvitationEmail(
    val inviterName: String,
    val inviterEmail: String,
    override val to: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val invitationToken: String,
) : EmailEvent

data class AccessRightsUpdateNotificationEmail(
    override val to: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val updatedByName: String,
    val updatedByEmail: String,
    val newAccessRights: Kayttooikeustaso,
) : EmailEvent

data class RemovalFromHankeNotificationEmail(
    override val to: String,
    val hankeTunnus: String,
    val hankeNimi: String,
    val deletedByName: String,
    val deletedByEmail: String,
) : EmailEvent

data class InformationRequestEmail(
    override val to: String,
    val hakemusNimi: String,
    val hakemusTunnus: String,
    val hakemusId: Long,
) : EmailEvent
