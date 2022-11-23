package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_DATE
import fi.hel.haitaton.hanke.MAXIMUM_TYOMAAKATUOSOITE_LENGTH
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
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

        // Must be from the begin of today or later, and earlier than some relevant maximum date
        // TODO: past date should only be prevented during creation of new hanke, not when updating
        // one.
        //  (However, this is currently a situation which can not be validated correctly, as the
        // update-method here
        //  does not differentiate between updates during using wizard vs. updates being done weeks
        // afterward,
        //  where the date isn't even being changed (just that old date being doing a round-trip in
        // the UI).
        if (
            hanke.alkuPvm ==
                null /* || hanke.alkuPvm!!.isBefore(getCurrentTimeUTC().truncatedTo(ChronoUnit.DAYS)) */ ||
                hanke.alkuPvm!!.isAfter(MAXIMUM_DATE)
        ) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("alkuPvm")
                .addConstraintViolation()
            ok = false
        }
        // Must be from the begin of today or later, and earlier than some relevant maximum date,
        // and same or later than alkuPvm
        // TODO: past date should only be prevented during creation of new hanke, not when updating
        // one.
        //  (However, this is currently a situation which can not be validated correctly, as the
        // update-method here
        //  does not differentiate between updates during using wizard vs. updates being done weeks
        // afterward,
        //  where the date isn't even being changed (just that old date being doing a round-trip in
        // the UI).
        if (
            hanke.loppuPvm ==
                null /* || hanke.loppuPvm!!.isBefore(getCurrentTimeUTC().truncatedTo(ChronoUnit.DAYS)) */ ||
                hanke.loppuPvm!!.isAfter(MAXIMUM_DATE)
        ) {
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

        if (hanke.saveType == null) {
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("saveType")
                .addConstraintViolation()
            ok = false
        }

        //  TODO ok = ok && isValidHankeYhteystietos(hanke, context) removed mandatory checks for
        // now when
        //   front is not ready to add same rules and this confuses
        ok = ok && checkTyomaaTiedot(hanke, context)
        ok = ok && checkHaitat(hanke, context)

        return ok
    }

    /** Checks that each given Yhteystieto has valid data, or not given at all. */
    private fun isValidHankeYhteystietos(
        hanke: Hanke,
        context: ConstraintValidatorContext
    ): Boolean {
        var ok = true
        hanke.omistajat.forEach { yhteystieto ->
            // mandatory
            ok = checkMandatoryYhteystietoData(yhteystieto, context, ok)
        }
        hanke.toteuttajat.forEach { yhteystieto ->
            // mandatory
            ok = checkMandatoryYhteystietoData(yhteystieto, context, ok)
        }
        hanke.muut.forEach { yhteystieto ->
            // mandatory
            ok = checkMandatoryYhteystietoData(yhteystieto, context, ok)
        }
        return ok
    }

    private fun checkMandatoryYhteystietoData(
        yhteystieto: HankeYhteystieto,
        context: ConstraintValidatorContext,
        ok: Boolean
    ): Boolean {
        var ok1 = ok
        // TODO: NOTE: having all four mandatory fields empty,
        //  but giving organisation is still valid for this... needs to be fixed.
        // Short version: Either all four mandatory fields must be have proper value,
        // or all of them must be empty/whitespace-only.
        if (yhteystieto.isAnyMandatoryFieldSet() && !yhteystieto.isValid()) {
            // TODO: is that property node correct?
            // TODO: Does not currently matter, though, as the node information does not get through
            // to error response.
            context
                .buildConstraintViolationWithTemplate(HankeError.HAI1002.toString())
                .addPropertyNode("YhteysTiedot")
                .addConstraintViolation()
            ok1 = false
        }
        return ok1
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
