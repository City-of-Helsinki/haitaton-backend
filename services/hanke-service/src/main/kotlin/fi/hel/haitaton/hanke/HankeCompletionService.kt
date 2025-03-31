package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import java.time.LocalDate
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeCompletionService(
    private val hankeRepository: HankeRepository,
    @Value("\${haitaton.hanke.completions.max-per-run}") private val completionsPerDay: Int,
) {

    @Transactional(readOnly = true)
    fun getPublicIds(): List<Int> {
        return hankeRepository.findHankeToComplete(completionsPerDay)
    }

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
    }

    companion object {
        private val COMPLETED_EXCAVATION_NOTIFICATION_STATUSES: Set<ApplicationStatus> =
            setOf(ApplicationStatus.FINISHED, ApplicationStatus.ARCHIVED)

        private val COMPLETED_CABLE_REPORT_STATUSES: Set<ApplicationStatus> =
            COMPLETED_EXCAVATION_NOTIFICATION_STATUSES + ApplicationStatus.DECISION

        fun assertHankePublic(hanke: HankeEntity) {
            if (hanke.status != HankeStatus.PUBLIC) throw HankeNotPublicException(hanke)
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
                    hakemus.alluStatus == null || hasCompletedStatus(hakemus)
                }

            activeApplications.forEach {
                logger.info {
                    "Hanke has an active application with status ${it.alluStatus} ${it.logString()} ${hanke.logString()}"
                }
            }

            return activeApplications.isNotEmpty()
        }

        private fun hasCompletedStatus(hakemus: HakemusEntity): Boolean =
            hakemus.alluStatus in
                when (hakemus.applicationType) {
                    ApplicationType.CABLE_REPORT -> COMPLETED_CABLE_REPORT_STATUSES
                    ApplicationType.EXCAVATION_NOTIFICATION ->
                        COMPLETED_EXCAVATION_NOTIFICATION_STATUSES
                }
    }
}

class HankeNotPublicException(hanke: HankeEntity) :
    HankeValidityException("Hanke is not public, it's ${hanke.status}", hanke)

class PublicHankeHasNoAreasException(hanke: HankeIdentifier) :
    HankeValidityException("Public hanke has no alueet", hanke)

class HankealueWithoutEndDateException(hanke: HankeIdentifier) :
    HankeValidityException("Public hanke has an alue without an end date", hanke)

open class HankeValidityException(message: String, hanke: HankeIdentifier) :
    RuntimeException("$message ${hanke.logString()}")
