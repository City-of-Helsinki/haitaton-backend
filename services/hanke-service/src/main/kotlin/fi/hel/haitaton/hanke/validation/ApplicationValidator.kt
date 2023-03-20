package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.InvalidApplicationDataException
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.isValid
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class ApplicationValidator : ConstraintValidator<ValidApplication, Application> {
    override fun isValid(application: Application?, context: ConstraintValidatorContext?): Boolean {

        val applicationData =
            application?.applicationData
                ?: throw InvalidApplicationDataException("Application data missing.")

        return when (applicationData) {
            is CableReportApplicationData ->
                listOfNotNull(
                        applicationData.customerWithContacts.customer,
                        applicationData.contractorWithContacts.customer,
                        applicationData.representativeWithContacts?.customer,
                        applicationData.propertyDeveloperWithContacts?.customer,
                    )
                    .all { it.registryKey == null || it.registryKey.isValid() }
        }
    }
}
