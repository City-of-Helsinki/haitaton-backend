package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators
import fi.hel.haitaton.hanke.validation.Validators.validate
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class HakemusUpdateRequestValidator :
    ConstraintValidator<ValidHakemusUpdateRequest, HakemusUpdateRequest> {

    override fun isValid(
        request: HakemusUpdateRequest,
        context: ConstraintValidatorContext?
    ): Boolean {
        val result =
            when (request) {
                is JohtoselvityshakemusUpdateRequest -> validate { request.validateForErrors() }
            }
        return result.okOrThrow()
    }

    /** Validate draft application. Checks only fields that have some actual data. */
    private fun JohtoselvityshakemusUpdateRequest.validateForErrors(): ValidationResult =
        validate { Validators.notJustWhitespace(name, "name") }
            .and { Validators.notJustWhitespace(workDescription, "workDescription") }
            .andWhen(startTime != null && endTime != null) {
                Validators.isBeforeOrEqual(startTime!!, endTime!!, "endTime")
            }
            .whenNotNull(postalAddress) { it.validateForErrors("postalAddress") }
            .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
            .whenNotNull(contractorWithContacts) { it.validateForErrors("contractorWithContacts") }
            .whenNotNull(representativeWithContacts) {
                it.validateForErrors("representativeWithContacts")
            }
            .whenNotNull(propertyDeveloperWithContacts) {
                it.validateForErrors("propertyDeveloperWithContacts")
            }

    private fun PostalAddressRequest.validateForErrors(path: String) = validate {
        Validators.notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName")
    }

    private fun CustomerWithContactsRequest.validateForErrors(path: String): ValidationResult =
        customer.validateForErrors("$path.customer")

    private fun CustomerRequest.validateForErrors(path: String): ValidationResult =
        validate { Validators.notJustWhitespace(name, "$path.name") }
            .and { Validators.notJustWhitespace(email, "$path.email") }
            .and { Validators.notJustWhitespace(phone, "$path.phone") }
            .andWhen(
                registryKey != null &&
                    (type == CustomerType.COMPANY || type == CustomerType.ASSOCIATION)
            ) {
                Validators.validateTrue(registryKey.isValidBusinessId(), "$path.registryKey")
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
