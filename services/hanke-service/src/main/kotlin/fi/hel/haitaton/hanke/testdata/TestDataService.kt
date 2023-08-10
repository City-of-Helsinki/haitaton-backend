package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.application.ApplicationRepository
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class TestDataService(
    private val applicationRepository: ApplicationRepository,
) {
    @Transactional
    fun unlinkApplicationsFromAllu() {
        logger.warn { "Unlinking all applications from Allu." }
        applicationRepository.findAll().forEach {
            logger.warn {
                "Unlinking application from Allu id=${it.id} alluid=${it.alluid} " +
                    "applicationIdentifier=${it.applicationIdentifier}."
            }
            it.alluid = null
            it.alluStatus = null
            it.applicationIdentifier = null
            it.applicationData = it.applicationData.copy(pendingOnClient = true)
        }
    }
}
