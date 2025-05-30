package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.HankeEntity.Companion.endDate
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import fi.hel.haitaton.hanke.domain.HankeReminder
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.email.EmailEvent
import fi.hel.haitaton.hanke.email.HankeCompletedNotification
import fi.hel.haitaton.hanke.email.HankeDeletionReminder
import fi.hel.haitaton.hanke.email.HankeEndingReminder
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.permissions.HankeKayttaja
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import java.time.Clock
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
    fun idsToComplete(): List<Int> = hankeRepository.findHankeToComplete(completionsPerDay)

    @Transactional(readOnly = true)
    fun idsForReminders(reminder: HankeReminder, clock: Clock = Clock.system(TZ_UTC)): List<Int> =
        hankeRepository.findHankeToRemind(
            completionsPerDay,
            reminderDate(reminder, clock),
            reminder,
        )

    @Transactional(readOnly = true)
    fun idsToDelete(clock: Clock = Clock.systemUTC()): List<Int> =
        hankeRepository.findHankeToDelete(
            OffsetDateTime.now(clock).minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
        )

    @Transactional(readOnly = true)
    fun idsForDeletionReminders(clock: Clock = Clock.systemUTC()): List<Int> =
        hankeRepository.findIdsForDeletionReminders(
            reminderDate(HankeReminder.DELETION_5, clock),
            HankeReminder.DELETION_5,
        )

    @Transactional
    fun idsForDraftsToComplete(): List<Int> =
        hankeRepository.findDraftsToComplete(
            completionsPerDay,
            getCurrentTimeUTCAsLocalTime().minusDays(DAYS_BEFORE_COMPLETING_DRAFT),
        )

    @Transactional
    fun completeHankeIfPossible(id: Int) {
        logger.info { "Checking hanke completion for $id" }
        val hanke = hankeRepository.getReferenceById(id)

        val hasFutureAreas =
            when (hanke.status) {
                HankeStatus.COMPLETED -> throw HankeCompletedException(hanke)
                HankeStatus.PUBLIC -> {
                    hanke.areasForPublic().hasFutureAreas()
                        ?: throw HankealueWithoutEndDateException(hanke)
                }
                HankeStatus.DRAFT -> hanke.areasForDraft()?.hasFutureAreas() ?: return
            }

        if (hasFutureAreas) {
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
    fun sendReminderIfNecessary(
        id: Int,
        reminder: HankeReminder,
        clock: Clock = Clock.system(TZ_UTC),
    ) {
        logger.info { "Checking if a reminder needs to be sent to hanke $id" }
        val hanke = hankeRepository.getReferenceById(id)

        val endDate =
            when (hanke.status) {
                HankeStatus.COMPLETED -> throw HankeCompletedException(hanke)
                HankeStatus.PUBLIC -> {
                    hanke.areasForPublic().endDate()
                        ?: throw HankealueWithoutEndDateException(hanke)
                }
                HankeStatus.DRAFT -> hanke.areasForDraft()?.endDate() ?: return
            }

        if (hanke.sentReminders.contains(reminder)) {
            logger.info {
                "Hanke has been sent this reminder already, so not sending it again. " +
                    "reminder=$reminder, ${hanke.logString()}"
            }
            return
        }

        if (endDate.isAfter(reminderDate(reminder, clock))) {
            return
        }

        if (!endDate.isAfter(LocalDate.now(clock))) {
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
                !endDate.isAfter(reminderDate(HankeReminder.COMPLETION_5, clock))
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
        val deletionDate = hanke.deletionDate() ?: throw HankeHasNoCompletionDateException(hanke)
        if (deletionDate.isAfter(LocalDate.now())) {
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

    @Transactional
    fun sendDeletionRemindersIfNecessary(id: Int, clock: Clock = Clock.systemUTC()) {
        logger.info { "Trying to send deletion reminders for hanke $id" }
        val hanke = hankeRepository.getReferenceById(id)

        assertHankeCompleted(hanke)
        if (hanke.sentReminders.contains(HankeReminder.DELETION_5)) {
            logger.info {
                "Hanke has been sent the deletion reminder already, so not sending it again. " +
                    hanke.logString()
            }
            return
        }
        val deletionDate = hanke.deletionDate() ?: throw HankeHasNoCompletionDateException(hanke)

        if (deletionDate.minusDays(5).isAfter(LocalDate.now(clock))) {
            logger.info { "Deletion notification is not yet due. ${hanke.logString()}" }
            return
        }

        if (!deletionDate.isAfter(LocalDate.now(clock))) {
            logger.info {
                "Hanke is due to be deleted, not sending reminders anymore. ${hanke.logString()}"
            }
            // Mark this reminder as sent so it doesn't come up again unless the hanke end date
            // changes.
            hanke.sentReminders += HankeReminder.DELETION_5
            return
        }

        hanke.sentReminders += HankeReminder.DELETION_5
        sendDeletionReminders(hanke, deletionDate)
    }

    @Transactional
    fun completeDraftHankeIfPossible(id: Int) {
        logger.info { "Checking if hanke $id has been idle for 180 days and could be removed." }
        val hanke = hankeRepository.getReferenceById(id)

        val modifiedLongAgo =
            when (hanke.status) {
                HankeStatus.COMPLETED -> throw HankeNotDraftException(hanke)
                HankeStatus.PUBLIC -> throw HankeNotDraftException(hanke)
                HankeStatus.DRAFT ->
                    hanke.modifiedAt?.isBefore(
                        getCurrentTimeUTCAsLocalTime().minusDays(DAYS_BEFORE_COMPLETING_DRAFT)
                    ) ?: false
            }

        if (!modifiedLongAgo) {
            logger.info {
                "Hanke has been modified recently, not doing anything. modifiedAt=${hanke.modifiedAt} ${hanke.logString()}"
            }
            return
        }

        if (hanke.alueet.isNotEmpty() && hanke.alueet.all { it.haittaLoppuPvm != null }) {
            logger.info {
                "Draft hanke has end dates for all their areas, not handling it here. ${hanke.logString()}"
            }
            return
        }

        if (hankeHasActiveApplications(hanke)) {
            logger.info {
                "Hanke has active applications, not doing anything. ${hanke.logString()}"
            }
            return
        }

        logger.info {
            "Draft hanke hasn't been updated in a long time, marking it completed. " +
                "modifiedAt=${hanke.modifiedAt} ${hanke.logString()}"
        }
        hanke.status = HankeStatus.COMPLETED
        hanke.completedAt = OffsetDateTime.now()

        sendCompletionNotifications(hanke)
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

    private fun sendDeletionReminders(hanke: HankeEntity, deletionDate: LocalDate) {
        hankeKayttajaService
            .getHankeKayttajatWithPermission(hanke.id, PermissionCode.EDIT)
            .forEach {
                applicationEventPublisher.publishEvent(
                    HankeDeletionReminder(
                        it.sahkoposti,
                        hanke.nimi,
                        hanke.hankeTunnus,
                        deletionDate,
                    )
                )
            }
    }

    companion object {
        const val DAYS_BEFORE_COMPLETING_DRAFT: Long = 180

        fun assertHankeCompleted(hanke: HankeEntity) {
            if (hanke.status != HankeStatus.COMPLETED) throw HankeNotCompletedException(hanke)
        }

        fun HankeEntity.areasForPublic(): MutableList<HankealueEntity> {
            if (alueet.isEmpty()) throw PublicHankeHasNoAreasException(this)
            return alueet
        }

        fun HankeEntity.areasForDraft(): MutableList<HankealueEntity>? {
            if (alueet.isEmpty()) {
                logger.info { "Draft hanke has no areas, not doing anything. ${logString()}" }
                return null
            }
            return alueet
        }

        /** Returns null if any areas are missing their end date. */
        fun List<HankealueEntity>.hasFutureAreas(): Boolean? = any {
            val loppuPvm = it.haittaLoppuPvm ?: return null
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

        fun reminderDate(reminder: HankeReminder, clock: Clock): LocalDate =
            when (reminder) {
                HankeReminder.COMPLETION_14 -> LocalDate.now(clock).plusDays(14)
                HankeReminder.COMPLETION_5 -> LocalDate.now(clock).plusDays(5)
                // The deletion date is compared to `completedAt`, while the others are compared to
                // the current date.
                HankeReminder.DELETION_5 ->
                    LocalDate.now(clock)
                        .minusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                        .plusDays(5)
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

class HankeNotDraftException(hanke: HankeEntity) :
    HankeValidityException("Hanke is not a draft, it's ${hanke.status}", hanke)

class HankeNotCompletedException(hanke: HankeEntity) :
    HankeValidityException("Hanke is not completed, it's ${hanke.status}", hanke)

class HankeCompletedException(hanke: HankeIdentifier) :
    HankeValidityException("Hanke has already been completed", hanke)

class PublicHankeHasNoAreasException(hanke: HankeIdentifier) :
    HankeValidityException("Public hanke has no alueet", hanke)

class HankealueWithoutEndDateException(hanke: HankeIdentifier) :
    HankeValidityException("Public hanke has an alue without an end date", hanke)

open class HankeValidityException(message: String, hanke: HankeIdentifier) :
    RuntimeException("$message ${hanke.logString()}")
