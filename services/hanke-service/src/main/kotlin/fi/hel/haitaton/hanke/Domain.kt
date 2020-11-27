package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.geojson.FeatureCollection
import java.time.ZonedDateTime
import javax.validation.ConstraintViolation

/*
Domain classes
 */


data class HankeGeometriat(
        var hankeId: String? = null,
        var featureCollection: FeatureCollection? = null,
        var version: Int? = null,
        var createdAt: ZonedDateTime? = null,
        var updatedAt: ZonedDateTime? = null
)


@JsonSerialize(using = HankeErrorSerializer::class)
enum class HankeError(
        val errorMessage: String) {
    HAI1001("Hanke not found"),
    HAI1002("Invalid Hanke data"),
    HAI1003("Internal error while saving Hanke"),
    HAI1004("Internal error while loading Hanke"),
    HAI1005("Database state invalid"),
    HAI1011("Invalid Hanke geometry"),
    HAI1012("Internal error while saving Hanke geometry"),
    HAI1013("Invalid coordinate system"),
    HAI1014("Internal error while loading Hanke geometry"),
    HAI1015("Hanke geometry not found");



    val errorCode: String
        get() = name

    companion object {
        fun valueOf(violation: ConstraintViolation<*>): HankeError {
            return valueOf(violation.message.split(":")[0])
        }

        val CODE_PATTERN = "HAI\\d{4}".toRegex()
    }

    override fun toString(): String {
        return "$name - $errorMessage"
    }
}

class HankeNotFoundException(val hankeId: String? = null) : RuntimeException(HankeError.HAI1001.errorMessage)

class DatabaseStateException(val hankeId: String? = null) : RuntimeException(HankeError.HAI1005.errorMessage)
