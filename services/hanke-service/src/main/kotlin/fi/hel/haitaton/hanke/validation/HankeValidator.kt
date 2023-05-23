package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_DATE
import fi.hel.haitaton.hanke.MAXIMUM_TYOMAAKATUOSOITE_LENGTH
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

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
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("nimi")
                .addConstraintViolation()
            ok = false
        }

        if (hanke.vaihe == null) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("vaihe")
                .addConstraintViolation()
            ok = false
        } else if (hanke.vaihe!! == Vaihe.SUUNNITTELU && hanke.suunnitteluVaihe == null) {
            // if vaihe = SUUNNITTELU then suunnitteluVaihe must have value
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("suunnitteluVaihe")
                .addConstraintViolation()
            ok = false
        }

        ok = ok && checkHankealueet(hanke, context)
        ok = ok && checkTyomaaTiedot(hanke, context)

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
                context
                    .buildConstraintViolationWithTemplate(HankeError.HAI1032.toString())
                    .addPropertyNode("haittaAlkuPvm")
                    .addConstraintViolation()
                return false
            }
            // Must be from the and earlier than some relevant maximum date,
            // and same or later than alkuPvm.
            // The end date can be in the past, since sometimes the permission to dig a hole is
            // applied for only after the hole has already been dug and covered.
            if (
                hankealue.haittaLoppuPvm == null || hankealue.haittaLoppuPvm!!.isAfter(MAXIMUM_DATE)
            ) {
                context
                    .buildConstraintViolationWithTemplate(HankeError.HAI1032.toString())
                    .addPropertyNode("haittaLoppuPvm")
                    .addConstraintViolation()
                return false
            }
            if (
                hankealue.haittaAlkuPvm != null &&
                    hankealue.haittaLoppuPvm != null &&
                    hankealue.haittaLoppuPvm!!.isBefore(hankealue.haittaAlkuPvm)
            ) {
                context
                    .buildConstraintViolationWithTemplate(HankeError.HAI1032.toString())
                    .addPropertyNode("haittaLoppuPvm")
                    .addConstraintViolation()
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
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("tyomaaKatuosoite")
                .addConstraintViolation()
            ok = false
        }

        return ok
    }
}
