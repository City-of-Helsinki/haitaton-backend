package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import fi.hel.haitaton.hanke.domain.HankeReminder
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.email.EmailEvent
import fi.hel.haitaton.hanke.email.HankeCompletedNotification
import fi.hel.haitaton.hanke.email.HankeEndingReminder
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.permissions.HankeKayttaja
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import java.time.LocalDate
import java.time.OffsetDateTime
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeCompletionService(
    private val hankeRepository: HankeRepository,
    private val hakemusService: HakemusService,
    private val hankeService: HankeService,
    private val hankeAttachmentService: HankeAttachmentService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val hankeLoggingService: HankeLoggingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @Value("\${haitaton.hanke.completions.max-per-run}") private val completionsPerDay: Int,
) {

    @Transactional(readOnly = true)
    fun getPublicIds(): List<Int> = hankeRepository.findHankeToComplete(completionsPerDay)

    @Transactional(readOnly = true)
    fun idsForReminders(reminder: HankeReminder): List<Int> =
        hankeRepository.findHankeToRemind(completionsPerDay, reminderDate(reminder), reminder)

    @Transactional(readOnly = true)
    fun idsToDelete(): List<Int> =
        hankeRepository.findHankeToDelete(OffsetDateTime.now().minusMonths(6))

    @Transactional
    fun completeHankeIfPossible(id: Int) {
        logger.info { "Checking hanke completion for $id" }
        val hanke = hankeRepository.getReferenceById(id)

        assertHankePublic(hanke)
        assertHankeHasAreas(hanke)

        if (hankeHasFutureAreas(hanke)) {
            logger.info {
                "Hanke has areas not in the past, not doing anything. ${hanke.logString()}"
            }
            return
        }

        if (hankeHasActiveApplications(hanke)) {
            logger.info {
                "Hanke has active applications, not doing anything. ${hanke.logString()}"
            }
            return
        }

        logger.info { "Hanke has been completed, marking it completed. ${hanke.logString()}" }
        hanke.status = HankeStatus.COMPLETED
        hanke.completedAt = OffsetDateTime.now()

        sendCompletionNotifications(hanke)
    }

    @Transactional
    fun sendReminderIfNecessary(id: Int, reminder: HankeReminder) {
        logger.info { "Checking if a reminder needs to be sent to hanke $id" }
        val hanke = hankeRepository.getReferenceById(id)

        assertHankePublic(hanke)
        assertHankeHasAreas(hanke)

        if (hanke.sentReminders.contains(reminder)) {
            logger.info {
                "Hanke has been sent this reminder already, so not sending it again. " +
                    "reminder=$reminder, ${hanke.logString()}"
            }
            return
        }

        val endDate = hanke.endDate() ?: throw HankealueWithoutEndDateException(hanke)

        if (endDate.isAfter(reminderDate(reminder))) {
            return
        }

        if (!endDate.isAfter(LocalDate.now())) {
            logger.info {
                "Hanke is due to be completed, not sending reminders anymore. ${hanke.logString()}"
            }
            // Mark this reminder as sent so it doesn't come up again unless the hanke end date
            // changes.
            hanke.sentReminders += reminder
            return
        }

        if (
            reminder == HankeReminder.COMPLETION_14 &&
                !endDate.isAfter(reminderDate(HankeReminder.COMPLETION_5))
        ) {
            logger.info {
                "Hanke is due to be sent the next reminder, so not sending this one. " +
                    "reminder = $reminder, ${hanke.logString()}"
            }
            hanke.sentReminders += reminder
            // Mark this reminder as sent so it doesn't come up again unless the hanke end date
            // changes.
            return
        }

        hanke.sentReminders += reminder
        sendReminders(hanke, endDate)
    }

    @Transactional
    fun deleteHanke(id: Int) {
        logger.info { "Trying to delete hanke $id" }
        val hanke = hankeRepository.getReferenceById(id)

        assertHankeCompleted(hanke)
        val deletionDate = hanke.deletionDate()
        if (deletionDate == null) {
            throw HankeHasNoCompletionDateException(hanke)
        } else if (deletionDate.isAfter(LocalDate.now())) {
            throw HankeCompletedRecently(hanke, deletionDate)
        }

        if (hankeHasActiveApplications(hanke)) {
            logger.error {
                "Hanke has active applications, not doing anything. ${hanke.logString()}"
            }
            return
        }

        hanke.hakemukset.forEach { hakemus -> hakemusService.deleteFromCompletedHanke(hakemus.id) }

        hankeAttachmentService.deleteAllAttachments(hanke)
        val domain = hankeService.loadHanke(hanke.hankeTunnus)!!
        hankeLoggingService.logDeleteFromHaitaton(domain)
        hankeRepository.deleteById(hanke.id)
    }

    private fun sendCompletionNotifications(hanke: HankeEntity) {
        sendEmailToEditors(hanke) {
            HankeCompletedNotification(it.sahkoposti, hanke.nimi, hanke.hankeTunnus)
        }
    }

    private fun sendReminders(hanke: HankeEntity, endingDate: LocalDate) {
        sendEmailToEditors(hanke) {
            HankeEndingReminder(it.sahkoposti, hanke.nimi, hanke.hankeTunnus, endingDate)
        }
    }

    private fun sendEmailToEditors(hanke: HankeEntity, email: (HankeKayttaja) -> EmailEvent) {
        hankeKayttajaService
            .getHankeKayttajatWithPermission(hanke.id, PermissionCode.EDIT)
            .forEach { applicationEventPublisher.publishEvent(email(it)) }
    }

    companion object {

        fun assertHankePublic(hanke: HankeEntity) {
            if (hanke.status != HankeStatus.PUBLIC) throw HankeNotPublicException(hanke)
        }

        fun assertHankeCompleted(hanke: HankeEntity) {
            if (hanke.status != HankeStatus.COMPLETED) throw HankeNotCompletedException(hanke)
        }

        fun assertHankeHasAreas(hanke: HankeEntity) {
            if (hanke.alueet.isEmpty()) throw PublicHankeHasNoAreasException(hanke)
        }

        fun hankeHasFutureAreas(hanke: HankeEntity): Boolean =
            hanke.alueet.any {
                val loppuPvm = it.haittaLoppuPvm ?: throw HankealueWithoutEndDateException(hanke)
                loppuPvm.isAfter(LocalDate.now())
            }

        fun hankeHasActiveApplications(hanke: HankeEntity): Boolean {
            val activeApplications =
                hanke.hakemukset.filterNot { hakemus ->
                    hakemus.alluStatus == null || hakemus.hasCompletedStatus()
                }

            activeApplications.forEach {
                logger.info {
                    "Hanke has an active application with status ${it.alluStatus} ${it.logString()} ${hanke.logString()}"
                }
            }

            return activeApplications.isNotEmpty()
        }

        fun reminderDate(reminder: HankeReminder): LocalDate =
            when (reminder) {
                HankeReminder.COMPLETION_14 -> LocalDate.now().plusDays(14)
                HankeReminder.COMPLETION_5 -> LocalDate.now().plusDays(5)
            }
    }
}

class HankeHasNoCompletionDateException(hanke: HankeEntity) :
    HankeValidityException("Hanke has no completion date", hanke)

class HankeCompletedRecently(hanke: HankeEntity, deletionDate: LocalDate?) :
    HankeValidityException(
        "Hanke has been completed too recently, on ${hanke.completedAt}," +
            " so it can not be deleted before $deletionDate.",
        hanke,
    )

class HankeNotCompletedException(hanke: HankeEntity) :
    HankeValidityException("Hanke is not completed, it's ${hanke.status}", hanke)

class HankeNotPublicException(hanke: HankeEntity) :
    HankeValidityException("Hanke is not public, it's ${hanke.status}", hanke)

class PublicHankeHasNoAreasException(hanke: HankeIdentifier) :
    HankeValidityException("Public hanke has no alueet", hanke)

class HankealueWithoutEndDateException(hanke: HankeIdentifier) :
    HankeValidityException("Public hanke has an alue without an end date", hanke)

open class HankeValidityException(message: String, hanke: HankeIdentifier) :
    RuntimeException("$message ${hanke.logString()}")
