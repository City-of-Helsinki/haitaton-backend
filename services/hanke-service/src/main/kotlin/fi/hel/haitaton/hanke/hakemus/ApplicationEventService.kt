package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.email.InformationRequestEmail
import fi.hel.haitaton.hanke.email.JohtoselvitysCompleteEmail
import fi.hel.haitaton.hanke.email.KaivuilmoitusDecisionEmail
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import fi.hel.haitaton.hanke.paatos.PaatosService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.taydennys.TaydennysService
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class ApplicationEventService(
    private val hakemusRepository: HakemusRepository,
    private val taydennysService: TaydennysService,
    private val paatosService: PaatosService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val muutosilmoitusService: MuutosilmoitusService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleApplicationEvent(alluId: Int, event: ApplicationStatusEvent) {
        val application = hakemusRepository.getOneByAlluid(alluId)
        if (application == null) {
            logger.error {
                "Allu had an event for a hakemus we don't have anymore. alluId=${alluId}"
            }
            return
        }

        logger.info {
            "Handling application event: " +
                "id=${application.id}, " +
                "alluId=${application.alluid}, " +
                "application identifier=${event.applicationIdentifier}, " +
                "new status=${event.newStatus}, " +
                "event time=${event.eventTime}"
        }

        fun updateStatus() {
            application.alluStatus = event.newStatus
            application.applicationIdentifier = event.applicationIdentifier
            logger.info {
                "Updating hakemus with new status, " +
                    "id=${application.id}, " +
                    "alluId=${application.alluid}, " +
                    "application identifier=${application.applicationIdentifier}, " +
                    "new status=${application.alluStatus}, " +
                    "event time=${event.eventTime}"
            }
        }

        when (event.newStatus) {
            ApplicationStatus.DECISION -> {
                muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(application)
                updateStatus()
                sendDecisionReadyEmails(application, event.applicationIdentifier)
                if (application.applicationType == ApplicationType.EXCAVATION_NOTIFICATION) {
                    paatosService.saveKaivuilmoituksenPaatos(application, event)
                }
            }
            ApplicationStatus.OPERATIONAL_CONDITION -> {
                updateStatus()
                when (application.applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        logger.error {
                            "Got ${event.newStatus} update for a cable report. ${application.logString()}"
                        }
                    ApplicationType.EXCAVATION_NOTIFICATION -> {
                        sendDecisionReadyEmails(application, event.applicationIdentifier)
                        paatosService.saveKaivuilmoituksenToiminnallinenKunto(application, event)
                    }
                }
            }
            ApplicationStatus.FINISHED -> {
                updateStatus()
                when (application.applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        logger.error {
                            "Got ${event.newStatus} update for a cable report. ${application.logString()}"
                        }
                    ApplicationType.EXCAVATION_NOTIFICATION -> {
                        sendDecisionReadyEmails(application, event.applicationIdentifier)
                        paatosService.saveKaivuilmoituksenTyoValmis(application, event)
                    }
                }
            }
            ApplicationStatus.REPLACED -> {
                logger.info {
                    "A decision has been replaced. Marking the old decisions as replaced. hakemustunnus = ${event.applicationIdentifier}, ${application.logString()}"
                }
                // Don't update application status or identifier. A new decision has been
                // made at the same time, so take the status and identifier from that update.
                paatosService.markReplaced(event.applicationIdentifier)
            }
            ApplicationStatus.WAITING_INFORMATION -> {
                muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(application)
                updateStatus()
                taydennysService.saveTaydennyspyyntoFromAllu(application)?.also {
                    sendInformationRequestEmails(application, event.applicationIdentifier)
                }
            }
            ApplicationStatus.HANDLING -> {
                taydennysService.removeTaydennyspyyntoIfItExists(application)
                updateStatus()
            }
            else -> updateStatus()
        }
    }

    private fun sendDecisionReadyEmails(application: HakemusEntity, applicationIdentifier: String) {
        val receivers = application.allContactUsers()

        if (receivers.isEmpty()) {
            logger.error {
                "No receivers found for hakemus ${application.alluStatus} ready email, not sending any. ${application.logString()}"
            }
            return
        }
        logger.info {
            "Sending hakemus ${application.alluStatus} ready emails to ${receivers.size} receivers"
        }

        receivers.forEach {
            val event =
                when (application.applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        JohtoselvitysCompleteEmail(
                            it.sahkoposti,
                            application.id,
                            applicationIdentifier,
                        )
                    ApplicationType.EXCAVATION_NOTIFICATION ->
                        KaivuilmoitusDecisionEmail(
                            it.sahkoposti,
                            application.id,
                            applicationIdentifier,
                        )
                }
            applicationEventPublisher.publishEvent(event)
        }
    }

    private fun sendInformationRequestEmails(hakemus: HakemusEntity, hakemusTunnus: String) {
        hakemus
            .allContactUsers()
            .filter { hankeKayttajaService.hasPermission(it, PermissionCode.EDIT_APPLICATIONS) }
            .forEach {
                applicationEventPublisher.publishEvent(
                    InformationRequestEmail(
                        it.sahkoposti,
                        hakemus.hakemusEntityData.name,
                        hakemusTunnus,
                        hakemus.id,
                    )
                )
            }
    }
}
