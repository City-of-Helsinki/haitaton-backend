package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Repository
import java.util.*

class ApplicationService(private val repo : ApplicationRepository) {
    fun create(application: Application) : Application {
        application.userId = SecurityContextHolder.getContext().authentication.name
        return repo.save(application);
    }

    fun getApplicationById(id: Long) : Optional<Application> {
        val userId = SecurityContextHolder.getContext().authentication.name
        return repo.findOneByIdAndUserId(id,userId)
    }

    fun getAllApplicationsForCurrentUser() : List<Application>{
        val userId = SecurityContextHolder.getContext().authentication.name
        return repo.getAllByUserId(userId)
    }

    fun updateApplicationData(id: Long, newApplicationData: JsonNode): Optional<Application> {
        val currentUser = SecurityContextHolder.getContext().authentication.name
        val found = repo.findOneByIdAndUserId(id, currentUser)
        if(found.isEmpty)
            return found

        val applicationToUpdate = found.get()
        applicationToUpdate.applicationData = newApplicationData

        return Optional.of(repo.save(applicationToUpdate))
    }
}

@Repository
interface ApplicationRepository : JpaRepository<Application, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String) : Optional<Application>
    fun getAllByUserId(userId: String): List<Application>
}