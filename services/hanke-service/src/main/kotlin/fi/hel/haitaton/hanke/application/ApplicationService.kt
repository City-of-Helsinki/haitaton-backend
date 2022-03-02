package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Repository

class ApplicationService(private val repo : ApplicationRepository) {
    fun create(application: ApplicationDTO) : ApplicationDTO {
        val userId = SecurityContextHolder.getContext().authentication.name
        val alluApplication = repo.save(AlluApplication(
                id = null,
                userId = userId,
                applicationType = application.applicationType,
                applicationData = application.applicationData
        ))
        return applicationToDto(alluApplication)
    }

    fun getApplicationById(id: Long) : ApplicationDTO? {
        val userId = SecurityContextHolder.getContext().authentication.name
        return repo.findOneByIdAndUserId(id, userId)?.let { applicationToDto(it) }
    }

    fun getAllApplicationsForCurrentUser() : List<ApplicationDTO> {
        val userId = SecurityContextHolder.getContext().authentication.name
        return repo.getAllByUserId(userId).map { applicationToDto(it) }
    }

    fun updateApplicationData(id: Long, newApplicationData: JsonNode): ApplicationDTO? {
        val currentUser = SecurityContextHolder.getContext().authentication.name
        val applicationToUpdate = repo.findOneByIdAndUserId(id, currentUser) ?: return null
        applicationToUpdate.applicationData = newApplicationData
        return applicationToDto(repo.save(applicationToUpdate))
    }

    /**
     * @return desired http status code. could also throw exceptions or create some enum for return type
     */
    fun sendApplication(id: Long): Int {
        val currentUser = SecurityContextHolder.getContext().authentication.name
        val application = repo.findOneByIdAndUserId(id, currentUser) ?: return 404

        // TODO: Make sure we haven't sent the application yet

        when (application.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                val cableReportApplication: CableReportApplication =
                        OBJECT_MAPPER.treeToValue(application.applicationData) ?: return 400
                // TODO: Try to send the application to ALLU
            }
        }

        // TODO: Mark application as sent, disallow further updates on the application
        return 200
    }

}

@Repository
interface ApplicationRepository : JpaRepository<AlluApplication, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String) : AlluApplication?
    fun getAllByUserId(userId: String): List<AlluApplication>
}