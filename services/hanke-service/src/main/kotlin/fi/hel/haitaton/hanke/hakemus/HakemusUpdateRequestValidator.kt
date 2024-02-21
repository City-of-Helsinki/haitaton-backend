package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.validation.ValidationResult
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class HakemusUpdateRequestValidator :
    ConstraintValidator<ValidHakemusUpdateRequest, HakemusUpdateRequest> {

    override fun isValid(
        request: HakemusUpdateRequest,
        context: ConstraintValidatorContext?
    ): Boolean {
        val result = request.validateForErrors()
        return result.okOrThrow()
    }

    private fun ValidationResult.okOrThrow(): Boolean {
        if (isOk()) {
            return true
        }
        throw InvalidHakemusDataException(errorPaths())
    }
}

class InvalidHakemusDataException(val errorPaths: List<String>) :
    RuntimeException(
        "Application contains invalid data. Errors at paths: ${errorPaths.joinToString { "applicationData.$it" }}"
    )
