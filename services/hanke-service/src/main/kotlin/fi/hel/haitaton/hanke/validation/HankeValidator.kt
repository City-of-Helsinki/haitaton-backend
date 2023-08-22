package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_DATE
import fi.hel.haitaton.hanke.MAXIMUM_TYOMAAKATUOSOITE_LENGTH
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
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

        var ok = true
        if (hanke.nimi.isNullOrBlank()) {
            context.addViolation("nimi", HankeError.HAI1002)
            ok = false
        }

        if (hanke.vaihe == null) {
            context.addViolation("vaihe", HankeError.HAI1002)
            ok = false
        } else if (hanke.vaihe!! == Vaihe.SUUNNITTELU && hanke.suunnitteluVaihe == null) {
            // if vaihe = SUUNNITTELU then suunnitteluVaihe must have value
            context.addViolation("suunnitteluVaihe", HankeError.HAI1002)
            ok = false
        }

        ok = ok && checkHankealueet(hanke, context)
        ok = ok && checkTyomaaTiedot(hanke, context)
        ok = ok && checkPerustaja(hanke, context)

        return ok
    }

    private fun checkHankealueet(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        for (hankealue in hanke.alueet) {
            // Must be earlier than some relevant maximum date.
            // The starting date can be in the past, since sometimes the permission to dig a hole is
            // applied for after the hole has already been dug.
            if (
                hankealue.haittaAlkuPvm == null || hankealue.haittaAlkuPvm!!.isAfter(MAXIMUM_DATE)
            ) {
                context.addViolation("haittaAlkuPvm", HankeError.HAI1032)
                return false
            }
            // Must be from the and earlier than some relevant maximum date,
            // and same or later than alkuPvm.
            // The end date can be in the past, since sometimes the permission to dig a hole is
            // applied for only after the hole has already been dug and covered.
            if (
                hankealue.haittaLoppuPvm == null || hankealue.haittaLoppuPvm!!.isAfter(MAXIMUM_DATE)
            ) {
                context.addViolation("haittaLoppuPvm", HankeError.HAI1032)
                return false
            }
            if (
                hankealue.haittaAlkuPvm != null &&
                    hankealue.haittaLoppuPvm != null &&
                    hankealue.haittaLoppuPvm!!.isBefore(hankealue.haittaAlkuPvm)
            ) {
                context.addViolation("haittaLoppuPvm", HankeError.HAI1032)
                return false
            }
        }
        return true
    }

    private fun checkTyomaaTiedot(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        var ok = true
        // tyomaaKatuosoite - either null or length <= maximum
        if (
            hanke.tyomaaKatuosoite != null &&
                hanke.tyomaaKatuosoite!!.length > MAXIMUM_TYOMAAKATUOSOITE_LENGTH
        ) {
            context.addViolation("tyomaaKatuosoite", HankeError.HAI1002)
            ok = false
        }

        return ok
    }

    private fun checkPerustaja(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        var ok = true

        with(hanke.perustaja) {
            if (this == null) {
                context.addViolation("perustaja", HankeError.HAI1002)
                return false
            }

            if (nimi.isNullOrBlank()) {
                context.addViolation("perustaja.nimi", HankeError.HAI1002)
                ok = false
            }

            if (email.isBlank()) {
                context.addViolation("perustaja.email", HankeError.HAI1002)
                ok = false
            }
        }

        return ok
    }

    private fun ConstraintValidatorContext.addViolation(propertyNode: String, error: HankeError) {
        buildConstraintViolationWithTemplate(error.toString())
            .addPropertyNode(propertyNode)
            .addConstraintViolation()
    }
}
