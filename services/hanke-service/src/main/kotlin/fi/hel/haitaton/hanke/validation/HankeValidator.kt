package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
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
        if (hanke.createdBy.isNullOrBlank()) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("createdBy").addConstraintViolation()
            ok = false
        }
        if (hanke.nimi.isNullOrBlank()) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("nimi").addConstraintViolation()
            ok = false
        }
        if (hanke.alkuPvm == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("alkuPvm").addConstraintViolation()
            ok = false
        }
        if (hanke.loppuPvm == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("loppuPvm").addConstraintViolation()
            ok = false
        }
        if (hanke.vaihe == null || !Vaihe.values().contains(hanke.vaihe)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("vaihe").addConstraintViolation()
            ok = false
        }
        // if vaihe = SUUNNITTELU then suunniteluVaihe must have value
        // notice: suunnitteluVaihe can be null but if it is not, then enum values needs to match
        if ((hanke.vaihe!!.equals(Vaihe.SUUNNITTELU) && hanke.suunnitteluVaihe == null) ||
                (hanke.suunnitteluVaihe != null && !SuunnitteluVaihe.values().contains(hanke.suunnitteluVaihe))) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("suunnitteluVaihe").addConstraintViolation()
            ok = false
        }
        if (hanke.saveType == null || !SaveType.values().contains(hanke.saveType)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("tallennus").addConstraintViolation()
            ok = false
        }

        return ok
    }
}