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

        // Must be earlier than some relevant maximum date.
        // The starting date can be in the past, since sometimes the permission to dig a hole is
        // applied for after the hole has already been dug.
        if (hanke.alkuPvm == null || hanke.alkuPvm!!.isAfter(MAXIMUM_DATE)) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("alkuPvm")
                .addConstraintViolation()
            ok = false
        }
        // Must be from the and earlier than some relevant maximum date,
        // and same or later than alkuPvm.
        // The end date can be in the past, since sometimes the permission to dig a hole is
        // applied for only after the hole has already been dug and covered.
        if (hanke.loppuPvm == null || hanke.loppuPvm!!.isAfter(MAXIMUM_DATE)) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("loppuPvm")
                .addConstraintViolation()
            ok = false
        }
        if (
            hanke.alkuPvm != null &&
                hanke.loppuPvm != null &&
                hanke.loppuPvm!!.isBefore(hanke.alkuPvm)
        ) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("loppuPvm")
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

        ok = ok && checkTyomaaTiedot(hanke, context)
        ok = ok && checkHaitat(hanke, context)

        return ok
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

    private fun checkHaitat(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        var ok = true
        // TODO: can haitta alku/loppu pvm be after the hanke ends?
        //  E.g. if agreed that another hanke will continue with the same hole soon after?
        // haittaAlkuPvm - either null or after alkuPvm and before maximum end date
        val alku = hanke.getHaittaAlkuPvm()
        val loppu = hanke.getHaittaLoppuPvm()

        if (alku != null && (alku.isBefore(hanke.alkuPvm) || alku.isAfter(MAXIMUM_DATE))) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("haittaAlkuPvm")
                .addConstraintViolation()
            ok = false
        }
        // haittaLoppuPvm - either null or after haittaAlkuPvm and before maximum end date
        if (loppu != null && (loppu.isBefore(alku) || loppu.isAfter(MAXIMUM_DATE))) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("haittaLoppuPvm")
                .addConstraintViolation()
            ok = false
        }

        return ok
    }
}
