package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.validate

object HakemusDataValidator {

    fun ensureValidForSend(data: HakemusData): Boolean {
        val result =
            when (data) {
                is JohtoselvityshakemusData ->
                    validate { data.validateForErrors() }.and { data.validateForMissing() }
                is KaivuilmoitusData -> data.validateForSend()
            }

        return result.okOrThrow()
    }
}

private fun ValidationResult.okOrThrow(): Boolean {
    if (isOk()) {
        return true
    }
    throw InvalidHakemusDataException(errorPaths())
}
