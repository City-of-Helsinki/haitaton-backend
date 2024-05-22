package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"
const val ALLU_USER_CANCELLATION_MSG = "Käyttäjä perui hakemuksen Haitattomassa."
const val ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG =
    "Haitaton ei saanut lisättyä hakemuksen liitteitä. Hakemus peruttu."

class ApplicationNotFoundException(id: Long) :
    RuntimeException("Application not found with id $id")

class ApplicationAlreadySentException(id: Long?, alluid: Int?, status: ApplicationStatus?) :
    RuntimeException("Application is already sent to Allu, id=$id, alluId=$alluid, status=$status")

class ApplicationAlreadyProcessingException(id: Long?, alluid: Int?) :
    RuntimeException("Application is no longer pending in Allu, id=$id, alluId=$alluid")

class ApplicationGeometryException(message: String) : RuntimeException(message)

class ApplicationGeometryNotInsideHankeException(message: String) : RuntimeException(message)

class ApplicationDecisionNotFoundException(message: String) : RuntimeException(message)
