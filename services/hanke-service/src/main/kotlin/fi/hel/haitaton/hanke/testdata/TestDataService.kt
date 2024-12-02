package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class TestDataService(private val hakemusRepository: HakemusRepository) {
    @Transactional
    fun unlinkApplicationsFromAllu() {
        logger.warn { "Unlinking all applications from Allu." }
        hakemusRepository.findAll().forEach {
            logger.warn { "Unlinking application from Allu. ${it.logString()}" }
            it.alluid = null
            it.alluStatus = null
            it.applicationIdentifier = null
            it.hakemusEntityData = it.hakemusEntityData.copy(paperDecisionReceiver = null)
        }
    }
}
