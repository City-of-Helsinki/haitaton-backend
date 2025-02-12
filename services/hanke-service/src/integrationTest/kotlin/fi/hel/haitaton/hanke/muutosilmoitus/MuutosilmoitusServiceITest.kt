package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasTargetType
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.test.resetCustomerIds
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MuutosilmoitusServiceITest(
    @Autowired private val muutosilmoitusService: MuutosilmoitusService,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val alluClient: AlluClient,
) : IntegrationTest() {

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluClient)
    }

    @Nested
    inner class Find {
        @Test
        fun `returns null when hakemus not found`() {
            val result = muutosilmoitusService.find(411)

            assertThat(result).isNull()
        }

        @Test
        fun `returns null when muutosilmoitus not found`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()

            val result = muutosilmoitusService.find(hakemus.id)

            assertThat(result).isNull()
        }

        @Test
        fun `returns muutosilmoitus when it exists`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()
            val muutosilmoitus = muutosilmoitusService.create(hakemus.id, USERNAME)

            val result = muutosilmoitusService.find(hakemus.id)

            assertThat(result).isNotNull().isEqualTo(muutosilmoitus)
        }
    }

    @Nested
    inner class Create {
        private fun builder() =
            hakemusFactory
                .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                .withMandatoryFields()
                .withStatus(ApplicationStatus.DECISION)

        @Test
        fun `throws exception when there's no hakemus`() {
            val failure = assertFailure { muutosilmoitusService.create(414, USERNAME) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("Hakemus not found with id 414")
            }
        }

        @Test
        fun `throws exception when the hakemus has the wrong status`() {
            val hakemus = builder().withStatus(ApplicationStatus.HANDLING).save()

            val failure = assertFailure { muutosilmoitusService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("status=HANDLING")
                messageContains("allowed statuses=DECISION, OPERATIONAL_CONDITION")
                messageContains("id=${hakemus.id}")
            }
        }

        @Test
        fun `creates the muutosilmoitus in the database`() {
            val hakemus = builder().save()

            muutosilmoitusService.create(hakemus.id, USERNAME)

            val muutosilmoitus = muutosilmoitusService.find(hakemus.id)
            val muutosilmoitusData = muutosilmoitus?.hakemusData?.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(muutosilmoitus)
                .isNotNull()
                .prop(Muutosilmoitus::hakemusId)
                .isEqualTo(hakemus.id)
            assertThat(muutosilmoitusData).isEqualTo(hakemusData)
        }

        @Test
        fun `returns the created muutosilmoitus`() {
            val hakemus = builder().save()

            val result = muutosilmoitusService.create(hakemus.id, USERNAME)

            val muutosilmoitusData = result.hakemusData.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(result).prop(Muutosilmoitus::hakemusId).isEqualTo(hakemus.id)
            assertThat(muutosilmoitusData).isEqualTo(hakemusData)
        }

        @Test
        fun `writes the created muutosilmoitus to the audit log`() {
            val hakemus = builder().save()
            auditLogRepository.deleteAll()

            val result = muutosilmoitusService.create(hakemus.id, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                withTarget {
                    hasTargetType(ObjectType.MUUTOSILMOITUS)
                    hasId(result.id)
                    hasNoObjectBefore()
                    hasObjectAfter(result)
                }
            }
        }
    }
}
