package fi.hel.haitaton.hanke.application.validation

import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.validate

/**
 * Validate required field are set. When application is a draft, it is ok to have fields that are
 * not yet defined. But e.g. when sending, they must be present.
 */
fun CableReportApplicationData.validateForMissing(): ValidationResult =
    validate { notBlank(name, "name") }
        .and { notBlank(workDescription, "workDescription") }
        .and { notNull(startTime, "startTime") }
        .and { notNull(endTime, "endTime") }
        .and { notNull(areas, "areas") }
        .and { notNull(rockExcavation, "rockExcavation") }
        .and { exactlyOneOrderer(customersWithContacts()) }
        .andWithNotNull(customerWithContacts, "customerWithContacts") { validateForMissing(it) }
        .andWithNotNull(contractorWithContacts, "contractorWithContacts") { validateForMissing(it) }
        .whenNotNull(representativeWithContacts) {
            it.validateForMissing("representativeWithContacts")
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForMissing("propertyDeveloperWithContacts")
        }
