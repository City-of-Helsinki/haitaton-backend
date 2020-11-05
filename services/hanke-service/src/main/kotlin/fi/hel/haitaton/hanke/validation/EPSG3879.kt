package fi.hel.haitaton.hanke.validation

import javax.validation.Constraint
import javax.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [FeatureCollectionValidator::class])
@MustBeDocumented
annotation class EPSG3879(
        val message: String = "{haitaton.hanke.geometria.notEPSG3879}",
        val groups: Array<KClass<*>> = [],
        val payload: Array<KClass<out Payload>> = []
)