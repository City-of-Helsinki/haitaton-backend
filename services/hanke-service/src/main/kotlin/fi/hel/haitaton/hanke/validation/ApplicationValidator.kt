package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.BaseApplication
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.validation.validateForErrors
import fi.hel.haitaton.hanke.application.validation.validateForMissing
import fi.hel.haitaton.hanke.validation.Validators.validate
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class ApplicationValidator : ConstraintValidator<ValidApplication, BaseApplication> {
    override fun isValid(
        application: BaseApplication,
        context: ConstraintValidatorContext?
    ): Boolean {

        val result =
            when (val data = application.applicationData) {
                is CableReportApplicationData -> validate { data.validateForErrors() }
            }

        return result.okOrThrow()
    }
}

object ApplicationDataValidator {

    fun ensureValidForSend(data: ApplicationData): Boolean {
        val result =
            when (data) {
                is CableReportApplicationData ->
                    validate { data.validateForErrors() }.and { data.validateForMissing() }
            }

        return result.okOrThrow()
    }
}

private fun ValidationResult.okOrThrow(): Boolean {
    if (isOk()) {
        return true
    }
    throw InvalidApplicationDataException(errorPaths())
}

class InvalidApplicationDataException(val errorPaths: List<String>) :
    RuntimeException(
        "Application contains invalid data. Errors at paths: ${errorPaths.joinToString { "applicationData.$it" }}"
    )
