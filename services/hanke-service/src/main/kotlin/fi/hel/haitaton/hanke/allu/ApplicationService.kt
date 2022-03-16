package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Repository
import java.lang.IllegalArgumentException

class ApplicationService(
        private val repo : ApplicationRepository,
        private val cableReportService : CableReportServiceAllu
) {

    fun getAllApplicationsForCurrentUser() : List<ApplicationDTO> {
        val userId = SecurityContextHolder.getContext().authentication.name
        return repo.getAllByUserId(userId).map { applicationToDto(it) }
    }

    fun getApplicationById(id: Long) = getById(id)?.let { applicationToDto(it) }

    fun create(application: ApplicationDTO) : ApplicationDTO {
        val userId = SecurityContextHolder.getContext().authentication.name
        val alluApplication = repo.save(AlluApplication(
                id = null,
                alluid = null,
                userId = userId,
                applicationType = application.applicationType,
                applicationData = application.applicationData
        ))
        trySendingPendingApplicationToAllu(alluApplication)
        return applicationToDto(alluApplication)
    }

    fun updateApplicationData(id: Long, newApplicationData: JsonNode): ApplicationDTO? {
        val application = getById(id) ?: return null

        if (!isUpdatable(application)) {
            throw IllegalArgumentException("Application already sent")
        }

        application.applicationData = newApplicationData
        trySendingPendingApplicationToAllu(application)

        return applicationToDto(repo.save(application))
    }

    fun sendApplication(id: Long): ApplicationDTO? {
        val application = getById(id) ?: return null
        if (!isUpdatable(application)) {
            throw IllegalArgumentException("Application already sent")
        }
        sendApplicationToAllu(application, pendingOnClient = false)
        return applicationToDto(repo.save(application))
    }

    private fun getById(id: Long): AlluApplication? {
        val userId = SecurityContextHolder.getContext().authentication.name
        return repo.findOneByIdAndUserId(id, userId)
    }

    private fun isUpdatable(application: AlluApplication): Boolean {
        if (application.alluid == null) {
            return true
        }
        // TODO: Check ALLU if current status is updatable
        return false
    }

    private fun trySendingPendingApplicationToAllu(application: AlluApplication) {
        try {
            sendApplicationToAllu(application, pendingOnClient = true)
        } catch (ignore: Exception) {
            // Just ignore it, maybe applicationData is missing something required which is fine
        }
    }

    private fun sendApplicationToAllu(application: AlluApplication, pendingOnClient: Boolean) {
        when (application.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                sendCableReport(application, pendingOnClient)
            }
        }
    }

    private fun sendCableReport(application: AlluApplication, pendingOnClient: Boolean) {
        val cableReportApplication: CableReportApplication = OBJECT_MAPPER.treeToValue(application.applicationData)!!
        cableReportApplication.pendingOnClient = pendingOnClient
        val alluid = cableReportService.create(cableReportApplication)
        application.alluid = alluid
        // Make sure pendingOnClient is up-to-date
        application.applicationData = OBJECT_MAPPER.valueToTree(cableReportApplication)
    }

}

@Repository
interface ApplicationRepository : JpaRepository<AlluApplication, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String) : AlluApplication?
    fun getAllByUserId(userId: String): List<AlluApplication>
}