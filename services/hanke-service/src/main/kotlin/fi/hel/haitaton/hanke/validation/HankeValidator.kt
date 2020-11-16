package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.Hanke
import fi.hel.haitaton.hanke.HankeError
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class HankeValidator : ConstraintValidator<ValidHanke, Hanke> {

    private fun checkInputString(input: String?): Boolean {
        return input == null || input.trim { it <= ' ' }.length == 0
    }

    private fun checkInputStringAllowNull(input: String?): Boolean {
        if (input == null) return false

        return input.trim { it <= ' ' }.length == 0
    }

    override fun isValid(hanke: Hanke?, context: ConstraintValidatorContext): Boolean {

        if (hanke == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1017.name).addConstraintViolation()
            return false
        }
        var ok = true
        if (checkInputStringAllowNull(hanke?.owner)) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1017.name).addPropertyNode("owner").addConstraintViolation()
            ok = false
        }
        when {
            checkInputStringAllowNull(hanke?.name) -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1017.name).addPropertyNode("name").addConstraintViolation()
                ok = false
            }
            hanke.phase!! > 7 -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1017.name).addPropertyNode("phase").addConstraintViolation()
                ok = false
            }
        }
        return ok
    }
}