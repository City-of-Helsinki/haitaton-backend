package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.ValidationResult.Companion.whenNotNull
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateTrue
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

/** Validate draft application. Checks only fields that have some actual data. */
fun HakemusUpdateRequest.validateForErrors(): ValidationResult =
    validateCommonFieldsForErrors().and {
        when (this) {
            is JohtoselvityshakemusUpdateRequest -> validateForErrors()
        }
    }

fun HakemusUpdateRequest.validateCommonFieldsForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
        }
        .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
        .whenNotNull(representativeWithContacts) {
            it.validateForErrors("representativeWithContacts")
        }

fun CustomerWithContactsRequest.validateForErrors(path: String): ValidationResult =
    customer.validateForErrors("$path.customer")

fun CustomerRequest.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(name, "$path.name") }
        .and { notJustWhitespace(email, "$path.email") }
        .and { notJustWhitespace(phone, "$path.phone") }
        .andWhen(
            registryKey != null &&
                (type == CustomerType.COMPANY || type == CustomerType.ASSOCIATION)
        ) {
            validateTrue(registryKey.isValidBusinessId(), "$path.registryKey")
        }

fun JohtoselvityshakemusUpdateRequest.validateForErrors(): ValidationResult =
    whenNotNull(postalAddress) { it.validateForErrors("postalAddress") }
        .whenNotNull(contractorWithContacts) { it.validateForErrors("contractorWithContacts") }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForErrors("propertyDeveloperWithContacts")
        }

fun PostalAddressRequest.validateForErrors(path: String) = validate {
    notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName")
}

class InvalidHakemusDataException(val errorPaths: List<String>) :
    RuntimeException(
        "Application contains invalid data. Errors at paths: ${errorPaths.joinToString { "applicationData.$it" }}"
    )
