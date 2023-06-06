package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.isValidBusinessId
import java.time.ZonedDateTime
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ApplicationValidator : ConstraintValidator<ValidApplication, Application> {
    override fun isValid(application: Application, context: ConstraintValidatorContext?): Boolean {

        return when (application.applicationData) {
            is CableReportApplicationData -> validOrThrow(application.applicationData)
        }
    }

    private fun validOrThrow(cableReportData: CableReportApplicationData): Boolean {
        if (!isValidCableReport(cableReportData)) {
            throw InvalidApplicationDataException("Received cable report contains invalid data.")
        }
        return true
    }

    private fun isValidCableReport(cableReportData: CableReportApplicationData): Boolean =
        listOf(
                { validStartAndEnd(cableReportData.startTime, cableReportData.endTime) },
                { validCustomersWithContacts(cableReportData.customersWithContacts()) }
            )
            .all { it() }

    /**
     * Checks if the start is before or equal to the end.
     *
     * @param start the start time (nullable)
     * @param end the end time (nullable)
     * @return true if start is before or equal to end, false if either is null or if start is after
     * end.
     */
    private fun validStartAndEnd(start: ZonedDateTime?, end: ZonedDateTime?): Boolean {
        logger.info { "Application startDate: '$start', endDate: '$end'." }

        if (start == null || end == null) {
            return true
        }

        return !start.isAfter(end)
    }

    /** Currently checks registryKey only. */
    private fun validCustomer(customer: Customer): Boolean {
        return validRegistryKey(customer.registryKey)
    }

    private fun validRegistryKey(key: String?) = key?.isValidBusinessId() ?: true

    /** Currently verifies customer only. */
    private fun validCustomersWithContacts(
        customerWithContacts: List<CustomerWithContacts>
    ): Boolean = customerWithContacts.all { validCustomer(it.customer) }
}

class InvalidApplicationDataException(message: String) : RuntimeException(message)
