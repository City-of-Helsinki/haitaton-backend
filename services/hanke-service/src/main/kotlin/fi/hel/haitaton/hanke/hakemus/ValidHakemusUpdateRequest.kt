package fi.hel.haitaton.hanke.hakemus

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HakemusUpdateRequestValidator::class])
@MustBeDocumented
annotation class ValidHakemusUpdateRequest(
    val message: String = "",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
