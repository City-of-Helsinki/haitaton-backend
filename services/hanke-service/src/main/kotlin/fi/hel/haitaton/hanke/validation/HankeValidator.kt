package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.MAXIMUM_DATE
import fi.hel.haitaton.hanke.MAXIMUM_TYOMAAKATUOSOITE_LENGTH
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.time.temporal.ChronoUnit
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class HankeValidator : ConstraintValidator<ValidHanke, Hanke> {

    /**
     *  isValid collects all the validation errors and returns them
     */
    override fun isValid(hanke: Hanke?, context: ConstraintValidatorContext): Boolean {
        if (hanke == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addConstraintViolation()
            return false
        }

        var ok = true
        if (hanke.nimi.isNullOrBlank()) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("nimi").addConstraintViolation()
            ok = false
        }

        // Must be from the begin of today or later, and earlier than some relevant maximum date
        if (hanke.alkuPvm == null || hanke.alkuPvm!!.isBefore(getCurrentTimeUTC().truncatedTo(ChronoUnit.DAYS)) || hanke.alkuPvm!!.isAfter(MAXIMUM_DATE)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("alkuPvm").addConstraintViolation()
            ok = false
        }
        // Must be from the begin of today or later, and earlier than some relevant maximum date, and same or later than alkuPvm
        if (hanke.loppuPvm == null || hanke.loppuPvm!!.isBefore(getCurrentTimeUTC().truncatedTo(ChronoUnit.DAYS)) || hanke.loppuPvm!!.isAfter(MAXIMUM_DATE)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("loppuPvm").addConstraintViolation()
            ok = false
        }
        if (hanke.alkuPvm != null && hanke.loppuPvm != null && hanke.loppuPvm!!.isBefore(hanke.alkuPvm)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("loppuPvm").addConstraintViolation()
            ok = false
        }

        // if vaihe = SUUNNITTELU then suunniteluVaihe must have value
        if ((hanke.vaihe!!.equals(Vaihe.SUUNNITTELU) && hanke.suunnitteluVaihe == null)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("suunnitteluVaihe").addConstraintViolation()
            ok = false
        }

        ok = ok && isValidHankeYhteystietos(hanke, context)
        ok = ok && checkTyomaaTiedot(hanke, context)
        ok = ok && checkHaitat(hanke, context)

        return ok
    }

    /**
     * Checks that each given Yhteystieto has valid data, or not given at all.
     */
    private fun isValidHankeYhteystietos(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        var ok = true
        hanke.omistajat.forEach { yhteystieto ->
            // mandatory
            ok = checkMandatoryYhteystietoData(yhteystieto, context, ok)
        }
        hanke.toteuttajat.forEach { yhteystieto ->
            // mandatory
            ok = checkMandatoryYhteystietoData(yhteystieto, context, ok)
        }
        hanke.arvioijat.forEach { yhteystieto ->
            // mandatory
            ok = checkMandatoryYhteystietoData(yhteystieto, context, ok)
        }
        return ok
    }

    private fun checkMandatoryYhteystietoData(yhteystieto: HankeYhteystieto, context: ConstraintValidatorContext, ok: Boolean): Boolean {
        var ok1 = ok
        if (!yhteystieto.sukunimi.isNullOrBlank() || !yhteystieto.etunimi.isNullOrBlank()
                || !yhteystieto.email.isNullOrBlank() || !yhteystieto.puhelinnumero.isNullOrBlank()) {
            // if any of the attributes contains something then all must exist
            if (yhteystieto.sukunimi.isNullOrBlank() || yhteystieto.etunimi.isNullOrBlank()
                    || yhteystieto.email.isNullOrBlank() || yhteystieto.puhelinnumero.isNullOrBlank()) {
                // TODO: is that property node correct?
                // TODO: Does not currently matter, though, as the node information does not get through to error response.
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("YhteysTiedot").addConstraintViolation()
                ok1 = false
            }
        }
        return ok1
    }

    private fun checkTyomaaTiedot(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        var ok = true
        // tyomaaKatuosoite - either null or length <= maximum
        if (hanke.tyomaaKatuosoite != null && hanke.tyomaaKatuosoite!!.length > MAXIMUM_TYOMAAKATUOSOITE_LENGTH) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("tyomaaKatuosoite").addConstraintViolation()
            ok = false
        }

        return ok
    }

    private fun checkHaitat(hanke: Hanke, context: ConstraintValidatorContext): Boolean {
        var ok = true
        // TODO: can haitta start/end after the hanke ends? E.g. if agreed that another hanke will continue with the same hole soon after?
        // haittaAlkuPvm - either null or after alkuPvm and before maximum end date
        if (hanke.haittaAlkuPvm != null && (hanke.haittaAlkuPvm!!.isBefore(hanke.alkuPvm) || hanke.haittaAlkuPvm!!.isAfter(MAXIMUM_DATE))) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("haittaAlkuPvm").addConstraintViolation()
            ok = false
        }
        // haittaLoppuPvm - either null or after haittaAlkuPvm and before maximum end date
        if (hanke.haittaLoppuPvm != null && (hanke.haittaLoppuPvm!!.isBefore(hanke.haittaAlkuPvm) || hanke.haittaLoppuPvm!!.isAfter(MAXIMUM_DATE))) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("haittaLoppuPvm").addConstraintViolation()
            ok = false
        }

        return ok
    }

}
