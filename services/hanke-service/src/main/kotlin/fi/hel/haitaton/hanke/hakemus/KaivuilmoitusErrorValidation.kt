package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.isValidOVT
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateTrue

/** Validate draft application. Checks only fields that have some actual data. */
fun KaivuilmoitusData.validateForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .and { atMostOneOrderer(yhteystiedot()) }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
        }
        .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
        .whenNotNull(contractorWithContacts) { it.validateForErrors("contractorWithContacts") }
        .whenNotNull(representativeWithContacts) {
            it.validateForErrors("representativeWithContacts")
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForErrors("propertyDeveloperWithContacts")
        }
        .whenNotNull(invoicingCustomer) { it.validateForErrors("invoicingCustomer") }
        .whenNotNull(additionalInfo) { notJustWhitespace(it, "additionalInfo") }

private fun Laskutusyhteystieto.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(nimi, "$path.name") }
        .whenNotNull(ytunnus) { validateTrue(it.isValidBusinessId(), "$path.registryKey") }
        .andWhen(!ovttunnus.isNullOrBlank()) { validateTrue(ovttunnus.isValidOVT(), "$path.ovt") }
        .and { notJustWhitespace(ovttunnus, "$path.ovt") }
        .and { notJustWhitespace(valittajanTunnus, "$path.invoicingOperator") }
        .andWhen(ovttunnus.isNullOrBlank()) {
            notNullOrBlank(katuosoite, "$path.postalAddress.streetAddress")
                .and { notNullOrBlank(postinumero, "$path.postalAddress.postalCode") }
                .and { notNullOrBlank(postitoimipaikka, "$path.postalAddress.city") }
        }
        .and { notJustWhitespace(sahkoposti, "$path.email") }
        .and { notJustWhitespace(puhelinnumero, "$path.phone") }
