package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Repository

class ApplicationService(private val repo : ApplicationRepository, private val cableReportService : CableReportServiceAllu) {
    fun create(application: ApplicationDTO) : ApplicationDTO {
        val userId = SecurityContextHolder.getContext().authentication.name
        val alluApplication = repo.save(AlluApplication(
                id = null,
                alluid = null,
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

    fun updateApplicationData(id: Long, newApplicationData: JsonNode): Pair<Int, Any> {
        val currentUser = SecurityContextHolder.getContext().authentication.name
        val applicationToUpdate = repo.findOneByIdAndUserId(id, currentUser) ?: return Pair(404, "Application not found")

        if(applicationToUpdate.alluid != null){
            return Pair(403, "Application already sent")
        }

        applicationToUpdate.applicationData = newApplicationData
        return Pair(200, applicationToDto(repo.save(applicationToUpdate)))
    }

    /**
     * @return desired http status code. could also throw exceptions or create some enum for return type
     */
    fun sendApplication(id: Long): Pair<Int, Any> {
        val currentUser = SecurityContextHolder.getContext().authentication.name
        val application = repo.findOneByIdAndUserId(id, currentUser) ?: return Pair(404, "Application not found")

        if(application.alluid != null){
            return Pair(403, "Application already sent")
        }

        when (application.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                val cableReportApplication: CableReportApplication =
                        OBJECT_MAPPER.treeToValue(application.applicationData) ?: return Pair(400, "Json parsing error")
                application.alluid = cableReportService.create(cableReportApplication)
            }
        }
        return Pair(200, applicationToDto(repo.save(application)))
    }

}

@Repository
interface ApplicationRepository : JpaRepository<AlluApplication, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String) : AlluApplication?
    fun getAllByUserId(userId: String): List<AlluApplication>
}