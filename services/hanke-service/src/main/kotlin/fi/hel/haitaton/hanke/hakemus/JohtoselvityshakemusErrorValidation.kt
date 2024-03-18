package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateFalse
import fi.hel.haitaton.hanke.validation.Validators.validateTrue

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

private fun Hakemusyhteystieto.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(nimi, "$path.nimi") }
        .and { notJustWhitespace(sahkoposti, "$path.sahkoposti") }
        .and { notJustWhitespace(puhelinnumero, "$path.puhelinnumero") }
        .whenNotNull(ytunnus) { validateTrue(it.isValidBusinessId(), "$path.ytunnus") }
        .andAllIn(yhteyshenkilot, "$path.yhteyshenkilot", ::validateContactForErrors)

private fun validateContactForErrors(yhteyshenkilo: Hakemusyhteyshenkilo, path: String) =
    with(yhteyshenkilo) {
        validate { notJustWhitespace(etunimi, "$path.etunimi") }
            .and { notJustWhitespace(sukunimi, "$path.sukunimi") }
            .and { notJustWhitespace(sahkoposti, "$path.sahkoposti") }
            .and { notJustWhitespace(puhelin, "$path.puhelin") }
    }

private fun PostalAddress.validateForErrors(path: String) =
    validate { notJustWhitespace(postalCode, "$path.postalCode") }
        .and { notJustWhitespace(city, "$path.city") }
        .and { notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName") }

private fun atMostOneOrderer(yhteystiedot: List<Hakemusyhteystieto>): ValidationResult =
    validateFalse(yhteystiedot.tilaajaCount() > 1, "customersWithContacts[].contacts[].orderer")

fun List<Hakemusyhteystieto>.tilaajaCount() = flatMap { it.yhteyshenkilot }.count { it.tilaaja }
