package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateTrue

/**
 * Validate required field are set. When application is a draft, it is ok to have fields that are
 * not yet defined. But e.g. when sending, they must be present.
 */
// TODO when implementing sending of excavation announcement (HAI-1373), these rules should be
// checked against the actual requirements
fun KaivuilmoitusData.validateForMissing(): ValidationResult =
    validate { notBlank(name, "name") }
        .and { notNullOrBlank(workDescription, "workDescription") }
        .and { validateTrue(requiredCompetence, "requiredCompetence") }
        .and { notNull(startTime, "startTime") }
        .and { notNull(endTime, "endTime") }
        .and { notNull(areas, "areas") }
        .andWhen(!cableReportDone) { notNull(rockExcavation, "rockExcavation") }
        .and { validateTrue(requiredCompetence, "requiredCompetence") }
        .and { exactlyOneOrderer(yhteystiedot()) }
        .andWithNotNull(customerWithContacts, "customerWithContacts") { validateForMissing(it) }
        .andWithNotNull(contractorWithContacts, "contractorWithContacts") { validateForMissing(it) }
        .whenNotNull(representativeWithContacts) {
            it.validateForMissing("representativeWithContacts")
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForMissing("propertyDeveloperWithContacts")
        }
        .whenNotNull(invoicingCustomer) { it.validateForMissing("invoicingCustomer") }

private fun Laskutusyhteystieto.validateForMissing(path: String): ValidationResult =
    validate { notNull(tyyppi, "$path.type") }
        .and { notBlank(nimi, "$path.name") }
        .andWhen(tyyppi == CustomerType.COMPANY || tyyppi == CustomerType.ASSOCIATION) {
            validateTrue(ytunnus.isValidBusinessId(), "$path.registryKey")
        }
