package fi.hel.haitaton.hanke.application.validation

import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate

internal fun PostalAddress.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(postalCode, "$path.postalCode") }
        .and { notJustWhitespace(city, "$path.city") }
        .and { notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName") }
