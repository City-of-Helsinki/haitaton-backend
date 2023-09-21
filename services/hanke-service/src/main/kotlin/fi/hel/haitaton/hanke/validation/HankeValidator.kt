package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_DATE
import fi.hel.haitaton.hanke.MAXIMUM_HANKE_ALUE_NIMI_LENGTH
import fi.hel.haitaton.hanke.MAXIMUM_HANKE_NIMI_LENGTH
import fi.hel.haitaton.hanke.MAXIMUM_TYOMAAKATUOSOITE_LENGTH
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notLongerThan
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.validate
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class HankeValidator : ConstraintValidator<ValidHanke, Hanke> {

    /** isValid collects all the validation errors and returns them */
    override fun isValid(hanke: Hanke?, context: ConstraintValidatorContext): Boolean {
        if (hanke == null) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addConstraintViolation()
            return false
        }

        val hankeResult = hanke.validate()
        hankeResult.errorPaths().forEach { context.addViolation(HankeError.HAI1002, it) }

        val alueResult = validate().andAllIn(hanke.alueet, "alueet", ::validateHankeAlue)
        alueResult.errorPaths().forEach { context.addViolation(HankeError.HAI1032, it) }

        return hankeResult.isOk() && alueResult.isOk()
    }
}

private fun ConstraintValidatorContext.addViolation(error: HankeError, node: String) {
    buildConstraintViolationWithTemplate(error.toString())
        .addPropertyNode(node)
        .addConstraintViolation()
}

/** Doesn't check hanke alue, because they use a different error code. */
private fun Hanke.validate() =
    validate { notNullOrBlank(nimi, "nimi") }
        .whenNotNull(nimi) { it.notLongerThan(MAXIMUM_HANKE_NIMI_LENGTH, "nimi") }
        .and { notNull(vaihe, "vaihe") }
        .andWhen(vaihe == Vaihe.SUUNNITTELU) { notNull(suunnitteluVaihe, "suunnitteluVaihe") }
        .whenNotNull(tyomaaKatuosoite) {
            it.notLongerThan(MAXIMUM_TYOMAAKATUOSOITE_LENGTH, "tyomaaKatuosoite")
        }

private fun validateHankeAlue(hankealue: Hankealue, path: String) = hankealue.validate(path)

private fun Hankealue.validate(path: String) =
    validate()
        .whenNotNull(nimi) { it.notLongerThan(MAXIMUM_HANKE_ALUE_NIMI_LENGTH, "$path.nimi") }
        .and { notNull(haittaAlkuPvm, "$path.haittaAlkuPvm") }
        .whenNotNull(haittaAlkuPvm) { isBeforeOrEqual(it, MAXIMUM_DATE, "$path.haittaAlkuPvm") }
        .and { notNull(haittaLoppuPvm, "$path.haittaLoppuPvm") }
        .whenNotNull(haittaLoppuPvm) { isBeforeOrEqual(it, MAXIMUM_DATE, "$path.haittaLoppuPvm") }
        .andWhen(haittaAlkuPvm != null && haittaLoppuPvm != null) {
            isBeforeOrEqual(haittaAlkuPvm!!, haittaLoppuPvm!!, "$path.haittaLoppuPvm")
        }
