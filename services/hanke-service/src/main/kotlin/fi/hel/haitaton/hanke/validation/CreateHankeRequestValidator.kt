package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_HANKE_NIMI_LENGTH
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notLongerThan
import fi.hel.haitaton.hanke.validation.Validators.validate
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [CreateHankeRequestValidator::class])
@MustBeDocumented
annotation class ValidCreateHankeRequest(
    val message: String = "",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class CreateHankeRequestValidator :
    ConstraintValidator<ValidCreateHankeRequest, CreateHankeRequest> {
    override fun isValid(
        request: CreateHankeRequest?,
        context: ConstraintValidatorContext
    ): Boolean {
        if (request == null) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addConstraintViolation()
            return false
        }

        val result = request.validate()
        result.errorPaths().forEach { context.addViolation(HankeError.HAI1002, it) }

        return result.isOk()
    }

    private fun CreateHankeRequest.validate(): ValidationResult = validate {
        notBlank(nimi, "nimi")
            .and { nimi.notLongerThan(MAXIMUM_HANKE_NIMI_LENGTH, "nimi") }
            .and { perustaja.validate("perustaja") }
    }

    private fun HankePerustaja.validate(path: String): ValidationResult = validate {
        notBlank(sahkoposti, "$path.sahkoposti").and {
            notBlank(puhelinnumero, "$path.puhelinnumero")
        }
    }

    private fun ConstraintValidatorContext.addViolation(error: HankeError, node: String) {
        buildConstraintViolationWithTemplate(error.toString())
            .addPropertyNode(node)
            .addConstraintViolation()
    }
}
