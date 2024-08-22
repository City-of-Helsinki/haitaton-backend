package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate

/** Validate draft application. Checks only fields that have some actual data. */
fun JohtoselvityshakemusData.validateForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .and { atMostOneOrderer(yhteystiedot()) }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
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

private fun PostalAddress.validateForErrors(path: String) =
    validate { notJustWhitespace(postalCode, "$path.postalCode") }
        .and { notJustWhitespace(city, "$path.city") }
        .and { notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName") }
