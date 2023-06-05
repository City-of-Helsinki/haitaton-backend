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
        with(cableReportData) {
            listOf(
                    validStartAndEnd(startTime, endTime),
                    validCustomerWithContactsNotNull(customerWithContacts),
                    validCustomerWithContactsNullable(representativeWithContacts),
                    validCustomerWithContactsNullable(contractorWithContacts),
                    validCustomerWithContactsNullable(propertyDeveloperWithContacts)
                )
                .all { it }
        }

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

        if (start == null && end == null) {
            return true
        }

        if (start == null || end == null) {
            return false
        }

        return !start.isAfter(end)
    }

    /** Currently checks registryKey only. */
    private fun validCustomer(customer: Customer?): Boolean {
        if (customer == null) {
            return true
        }

        return validRegistryKey(customer.registryKey)
    }

    private fun validRegistryKey(key: String?) = key?.isValidBusinessId() ?: true

    /** Currently verifies customer only. */
    private fun validCustomerWithContactsNotNull(contacts: CustomerWithContacts): Boolean =
        validCustomer(contacts.customer)

    /** Currently verifies customer only. */
    private fun validCustomerWithContactsNullable(contacts: CustomerWithContacts?): Boolean {
        if (contacts == null) {
            return true
        }

        return validCustomer(contacts.customer)
    }
}

class InvalidApplicationDataException(message: String) : RuntimeException(message)
