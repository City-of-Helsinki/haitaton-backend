package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.application.BaseApplication
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateFalse
import fi.hel.haitaton.hanke.validation.Validators.validateTrue
import java.util.Locale
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class ApplicationValidator : ConstraintValidator<ValidApplication, BaseApplication> {
    override fun isValid(
        application: BaseApplication,
        context: ConstraintValidatorContext?
    ): Boolean {

        val result =
            when (val applicationData = application.applicationData) {
                is CableReportApplicationData -> applicationData.validate()
            }

        if (result.isOk()) {
            return true
        }

        throw InvalidApplicationDataException(result.errorPaths())
    }

    private fun CableReportApplicationData.validate(): ValidationResult =
        validate { notJustWhitespace(name, "name") }
            .and { notJustWhitespace(workDescription, "workDescription") }
            .and { notJustWhitespace(customerReference, "customerReference") }
            .and { atMostOneOrderer(customersWithContacts()) }
            .andWhen(startTime != null && endTime != null) {
                isBeforeOrEqual(startTime!!, endTime!!, "endTime")
            }
            .whenNotNull(postalAddress) { it.validate("postalAddress") }
            .and { customerWithContacts.validate("customerWithContacts") }
            .and { contractorWithContacts.validate("contractorWithContacts") }
            .whenNotNull(representativeWithContacts) { it.validate("representativeWithContacts") }
            .whenNotNull(propertyDeveloperWithContacts) {
                it.validate("propertyDeveloperWithContacts")
            }
            .whenNotNull(invoicingCustomer) { it.validate("invoicingCustomer") }

    private fun moreThanOneOrderer(customersWithContacts: List<CustomerWithContacts>): Boolean =
        customersWithContacts.flatMap { it.contacts }.filter { it.orderer }.size > 1

    private fun atMostOneOrderer(
        customersWithContacts: List<CustomerWithContacts>
    ): ValidationResult =
        validateFalse(
            moreThanOneOrderer(customersWithContacts),
            "customersWithContacts[].contacts[].orderer"
        )

    private fun CustomerWithContacts.validate(path: String): ValidationResult =
        customer.validate("$path.customer").andAllIn(contacts, "$path.contacts", ::validateContact)

    private fun Customer.validate(path: String): ValidationResult =
        validate { notJustWhitespace(name, "$path.name") }
            .and { validateTrue(Locale.getISOCountries().contains(country), "country") }
            .and { notJustWhitespace(email, "$path.email") }
            .and { notJustWhitespace(phone, "$path.phone") }
            .whenNotNull(registryKey) { validateTrue(it.isValidBusinessId(), "$path.registryKey") }
            .and { notJustWhitespace(ovt, "$path.ovt") }
            .and { notJustWhitespace(invoicingOperator, "$path.invoicingOperator") }
            .and { notJustWhitespace(sapCustomerNumber, "$path.sapCustomerNumber") }

    private fun Contact.validate(path: String): ValidationResult =
        validate { notJustWhitespace(firstName, "$path.firstName") }
            .and { notJustWhitespace(lastName, "$path.lastName") }
            .and { notJustWhitespace(email, "$path.email") }
            .and { notJustWhitespace(phone, "$path.phone") }

    private fun validateContact(contact: Contact, path: String) = contact.validate(path)

    private fun PostalAddress.validate(path: String) =
        validate { notJustWhitespace(postalCode, "$path.postalCode") }
            .and { notJustWhitespace(city, "$path.city") }
            .and { notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName") }
}

class InvalidApplicationDataException(errorPaths: List<String>) :
    RuntimeException(
        "Received application contains invalid data. Errors at paths: ${errorPaths.joinToString()}"
    )
