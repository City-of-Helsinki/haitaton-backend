package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.isValidOVT
import fi.hel.haitaton.hanke.validation.HenkilotunnusValidator.isValidHenkilotunnus
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.ValidationResult.Companion.whenNotNull
import fi.hel.haitaton.hanke.validation.Validators.given
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateFalse
import fi.hel.haitaton.hanke.validation.Validators.validateNull
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
            is KaivuilmoitusUpdateRequest -> validateForErrors()
        }
    }

fun HakemusUpdateRequest.validateCommonFieldsForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
        }

fun CustomerWithContactsRequest.validateForErrors(path: String): ValidationResult =
    customer.validateForErrors("$path.customer")

fun CustomerRequest.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(name, "$path.name") }
        .and { notJustWhitespace(email, "$path.email") }
        .and { notJustWhitespace(phone, "$path.phone") }
        .and { validateRegistryKey(path) }

fun CustomerRequest.validateRegistryKey(path: String): ValidationResult =
    validateRegistryKey(type, registryKey, registryKeyHidden, path)

fun InvoicingCustomerRequest.validateRegistryKey(path: String): ValidationResult =
    validateRegistryKey(type, registryKey, registryKeyHidden, path)

private fun validateRegistryKey(
    customerType: CustomerType,
    registryKey: String?,
    registryKeyHidden: Boolean,
    path: String,
) =
    when (customerType) {
        CustomerType.COMPANY ->
            validateRegistryKeyForCompanies(registryKey, registryKeyHidden, path)
        CustomerType.ASSOCIATION ->
            validateRegistryKeyForCompanies(registryKey, registryKeyHidden, path)
        CustomerType.PERSON -> validateRegistryKeyForPerson(registryKey, registryKeyHidden, path)
        CustomerType.OTHER -> validateRegistryKeyForOther(registryKey, registryKeyHidden, path)
    }

private fun validateRegistryKeyForCompanies(
    registryKey: String?,
    registryKeyHidden: Boolean,
    path: String,
) =
    validateFalse(registryKeyHidden, "$path.registryKeyHidden").and {
        whenNotNull(registryKey) {
            validateTrue(registryKey.isValidBusinessId(), "$path.registryKey")
        }
    }

private fun validateRegistryKeyForOther(
    registryKey: String?,
    registryKeyHidden: Boolean,
    path: String,
) =
    validate()
        .andWhen(registryKeyHidden) { validateNull(registryKey, "$path.registryKey") }
        .andWhen(!registryKeyHidden) {
            whenNotNull(registryKey) { notBlank(it, "$path.registryKey") }
        }

private fun validateRegistryKeyForPerson(
    registryKey: String?,
    registryKeyHidden: Boolean,
    path: String,
) =
    validate()
        .andWhen(registryKeyHidden) { validateNull(registryKey, "$path.registryKey") }
        .andWhen(!registryKeyHidden) {
            whenNotNull(registryKey) {
                validateTrue(it.isValidHenkilotunnus(), "$path.registryKey")
            }
        }

private fun JohtoselvityshakemusUpdateRequest.validateForErrors(): ValidationResult =
    whenNotNull(postalAddress) { it.validateForErrors("postalAddress") }
        .whenNotNull(customerWithContacts) {
            it.validateForErrors("customerWithContacts").and {
                it.validateNoHenkilotunnus("customerWithContacts")
            }
        }
        .whenNotNull(representativeWithContacts) {
            it.validateForErrors("representativeWithContacts").and {
                it.validateNoHenkilotunnus("representativeWithContacts")
            }
        }
        .whenNotNull(contractorWithContacts) {
            it.validateForErrors("contractorWithContacts").and {
                it.validateNoHenkilotunnus("contractorWithContacts")
            }
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForErrors("propertyDeveloperWithContacts").and {
                it.validateNoHenkilotunnus("propertyDeveloperWithContacts")
            }
        }

fun PostalAddressRequest.validateForErrors(path: String) = validate {
    notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName")
}

private fun KaivuilmoitusUpdateRequest.validateForErrors(): ValidationResult =
    given(!cableReportDone) { notNull(rockExcavation, "rockExcavation") }
        .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
        .whenNotNull(representativeWithContacts) {
            it.validateForErrors("representativeWithContacts").and {
                it.validateNoHenkilotunnus("representativeWithContacts")
            }
        }
        .whenNotNull(contractorWithContacts) {
            it.validateForErrors("contractorWithContacts").and {
                it.validateNoHenkilotunnus("contractorWithContacts")
            }
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForErrors("propertyDeveloperWithContacts").and {
                it.validateNoHenkilotunnus("propertyDeveloperWithContacts")
            }
        }
        .whenNotNull(invoicingCustomer) { it.validateForErrors("invoicingCustomer") }
        .and { notJustWhitespace(additionalInfo, "additionalInfo") }

private fun CustomerWithContactsRequest.validateNoHenkilotunnus(path: String) =
    customer.validateNoHenkilotunnus("$path.customer")

private fun CustomerRequest.validateNoHenkilotunnus(path: String) =
    validate().andWhen(type in listOf(CustomerType.PERSON, CustomerType.OTHER)) {
        validateNull(registryKey, "$path.registryKey")
    }

fun InvoicingCustomerRequest.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(name, "$path.name") }
        .and { validateRegistryKey(path) }
        .andWhen(!ovt.isNullOrBlank()) { validateTrue(ovt.isValidOVT(), "$path.ovt") }
        .and { notJustWhitespace(ovt, "$path.ovt") }
        .and { notJustWhitespace(invoicingOperator, "$path.invoicingOperator") }
        .andWhen(ovt.isNullOrBlank()) { notNull(postalAddress, "$path.postalAddress") }
        .whenNotNull(postalAddress) { it.validateForErrors("$path.postalAddress") }
        .and { notJustWhitespace(email, "$path.email") }
        .and { notJustWhitespace(phone, "$path.phone") }

fun InvoicingPostalAddressRequest.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(streetAddress?.streetName, "$path.streetAddress.streetName") }
        .and { notJustWhitespace(postalCode, "$path.postalCode") }
        .and { notJustWhitespace(city, "$path.city") }

class InvalidHakemusDataException(val errorPaths: List<String>) :
    RuntimeException(
        "Application contains invalid data. Errors at paths: ${errorPaths.joinToString { "applicationData.$it" }}")
