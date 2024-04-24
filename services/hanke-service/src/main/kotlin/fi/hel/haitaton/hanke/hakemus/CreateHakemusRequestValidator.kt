package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.hakemus.CreateHakemusRequestValidators.validateForErrors
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.ValidationResult.Companion.whenNotNull
import fi.hel.haitaton.hanke.validation.Validators.given
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.validate
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [CreateHakemusRequestValidator::class])
@MustBeDocumented
annotation class ValidCreateHakemusRequest(
    val message: String = "",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class CreateHakemusRequestValidator :
    ConstraintValidator<ValidCreateHakemusRequest, CreateHakemusRequest> {

    override fun isValid(
        request: CreateHakemusRequest,
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

object CreateHakemusRequestValidators {

    fun CreateHakemusRequest.validateForErrors(): ValidationResult =
        validate { notJustWhitespace(name, "name") }
            .and { notJustWhitespace(workDescription, "workDescription") }
            .and {
                when (this) {
                    is CreateJohtoselvityshakemusRequest -> validateForErrors()
                    is CreateKaivuilmoitusRequest -> validateForErrors()
                }
            }

    private fun CreateJohtoselvityshakemusRequest.validateForErrors(): ValidationResult =
        whenNotNull(postalAddress) { it.validateForErrors("postalAddress") }

    private fun CreateKaivuilmoitusRequest.validateForErrors(): ValidationResult =
        given(!cableReportDone) { notNull(rockExcavation, "rockExcavation") }
}
