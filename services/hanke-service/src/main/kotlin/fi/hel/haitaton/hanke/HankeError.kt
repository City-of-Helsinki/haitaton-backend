package fi.hel.haitaton.hanke

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonUnwrapped
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.ConstraintViolation

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Schema(enumAsRef = true)
enum class HankeError(val errorMessage: String) {
    HAI0001("Access denied"),
    HAI0002("Internal error"),
    HAI0003("Invalid data"),
    HAI0004("Resource does not exist"),
    HAI0005("Insufficient permissions"),
    HAI1001("Hanke not found"),
    HAI1002("Invalid Hanke data"),
    HAI1003("Internal error while saving Hanke"),
    HAI1004("Internal error while loading Hanke"),
    HAI1005("Database state invalid"),
    HAI1006("Internal error while creating tormaystarkastelu for Hanke"),
    HAI1007("Tormaystarkastelu not found"),
    HAI1008("Internal error while loading tormaystarkastelu for Hanke"),
    HAI1011("Invalid Hanke geometry"),
    HAI1012("Internal error while saving Hanke geometry"),
    HAI1013("Invalid coordinate system"),
    HAI1014("Internal error while loading Hanke geometry"),
    HAI1015("Hanke geometry not found"),
    HAI1020("HankeYhteystieto not found"),
    HAI1029("HankeYhteystieto personal data processing restricted"),
    HAI1030("Problem with classification of geometries"),
    HAI1031("Invalid state: Missing needed data"),
    HAI1032("Invalid Hankealue data"),
    HAI2001("Application not found"),
    HAI2002("Incompatible application data type"),
    HAI2003("Application is already processing in Allu and can no longer be updated."),
    HAI2004("Application data missing information needed by Allu."),
    HAI2005("Invalid application geometry"),
    HAI2006("Application decision not found"),
    HAI2007("Application geometry not inside any hankealue"),
    HAI2008("Application contains invalid data"),
    HAI2009("Application is already sent to Allu, operation prohibited."),
    HAI2010("Application contains invalid customer"),
    HAI2011("Application contains invalid contact"),
    HAI2012("User is not a contact on the application, operation forbidden"),
    HAI3001("Attachment upload failed"),
    HAI3002("Loading attachment failed"),
    HAI3003("Attachment limit reached"),
    HAI4001("HankeKayttaja not found"),
    HAI4002("Trying to change own permission"),
    HAI4003("Permission data conflict"),
    HAI4004("Kayttajatunniste not found"),
    HAI4005("Could not verify user identity"),
    HAI4006("Duplicate hankekayttaja"),
    HAI4007("Verified name not found in Profiili"),
    ;

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

data class HankeErrorDetail(
    @JsonUnwrapped val hankeError: HankeError,
    val errorPaths: List<String>
)

class HankeNotFoundException(val hankeTunnus: String?) :
    RuntimeException("Hanke not found with hankeTunnus $hankeTunnus")

class HankeArgumentException(message: String) : RuntimeException(message)

class HankeYhteystietoNotFoundException(val hanke: HankeIdentifier, ytId: Int) :
    RuntimeException(
        "HankeYhteystieto not found for Hanke, yhteystieto: $ytId, ${hanke.logString()}"
    )

class HankeAlluConflictException(message: String) : RuntimeException(message)

class DatabaseStateException(message: String) : RuntimeException(message)

class HankeYhteystietoProcessingRestrictedException(message: String) : RuntimeException(message)
