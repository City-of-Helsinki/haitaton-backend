package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsOnly
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
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
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
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaydennysServiceITest(
    @Autowired private val taydennysService: TaydennysService,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val taydennyspyyntoFactory: TaydennyspyyntoFactory,
    @Autowired private val auditLogRepository: AuditLogRepository,
) : IntegrationTest() {
    private val alluId = 3464

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
    inner class FindTaydennyspyynto {

        @Test
        fun `returns null when taydennyspyynto doesn't exist`() {
            val result = taydennysService.findTaydennyspyynto(1L)

            assertThat(result).isNull()
        }

        @Test
        fun `returns taydennyspyynto when it exists`() {
            val hakemus = hakemusFactory.builder().withStatus().save()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)

            val result = taydennysService.findTaydennyspyynto(hakemus.id)

            assertThat(result).isEqualTo(taydennyspyynto)
        }
    }

    @Nested
    inner class SaveTaydennyspyyntoFromAllu {

        @Test
        fun `saves taydennyspyynto when Allu has one for the application`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = alluId).save()
            val kentat =
                listOf(
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.CUSTOMER, "Customer is missing"),
                    AlluFactory.createInformationRequestField(
                        InformationRequestFieldKey.ATTACHMENT, "Needs a letter of attorney"),
                )
            every { alluClient.getInformationRequest(hakemus.alluid!!) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId, fields = kentat)

            taydennysService.saveTaydennyspyyntoFromAllu(hakemus)

            assertThat(taydennyspyyntoRepository.findAll()).single().all {
                prop(TaydennyspyyntoEntity::alluId)
                    .isEqualTo(AlluFactory.DEFAULT_INFORMATION_REQUEST_ID)
                prop(TaydennyspyyntoEntity::applicationId).isEqualTo(hakemus.id)
                prop(TaydennyspyyntoEntity::kentat)
                    .containsOnly(
                        InformationRequestFieldKey.CUSTOMER to "Customer is missing",
                        InformationRequestFieldKey.ATTACHMENT to "Needs a letter of attorney",
                    )
            }
            verifySequence { alluClient.getInformationRequest(hakemus.alluid!!) }
        }
    }

    @Nested
    inner class CreateFromHakemus {
        private val fixedUUID = UUID.fromString("789b38cf-5345-4889-a5b8-2711c47559c8")

        private fun builder() =
            hakemusFactory
                .builder()
                .withMandatoryFields()
                .withStatus(ApplicationStatus.WAITING_INFORMATION)

        @Test
        fun `throws exception when there's no hakemus`() {
            val failure = assertFailure { taydennysService.create(414, USERNAME) }

            failure.all {
                hasClass(NoTaydennyspyyntoException::class)
                messageContains("Application doesn't have an open taydennyspyynto")
                messageContains("hakemusId=414")
            }
        }

        @Test
        fun `throws exception when there's no taydennyspyynto on the hakemus`() {
            val hakemus = builder().save()

            val failure = assertFailure { taydennysService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(NoTaydennyspyyntoException::class)
                messageContains("Application doesn't have an open taydennyspyynto")
                messageContains("hakemusId=${hakemus.id}")
            }
        }

        @Test
        fun `throws exception when the hakemus has the wrong status`() {
            val hakemus = builder().withStatus(ApplicationStatus.HANDLING).save()
            taydennyspyyntoFactory.save(hakemus.id)

            val failure = assertFailure { taydennysService.create(hakemus.id, USERNAME) }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("allowed statuses=WAITING_INFORMATION")
                messageContains("id=${hakemus.id}")
            }
        }

        @Test
        fun `creates the taydennys in the database`() {
            val hakemus = builder().save()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)

            taydennysService.create(hakemus.id, USERNAME)

            val taydennys = taydennysService.findTaydennys(hakemus.id)
            val taydennysData = taydennys?.hakemusData?.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(taydennys)
                .isNotNull()
                .prop(Taydennys::taydennyspyyntoId)
                .isEqualTo(taydennyspyynto.id)
            assertThat(taydennysData).isEqualTo(hakemusData)
        }

        @Test
        fun `returns the created taydennys`() {
            val hakemus = builder().save()
            val taydennyspyynto = taydennyspyyntoFactory.save(hakemus.id)

            val response = taydennysService.create(hakemus.id, USERNAME)

            val taydennysData = response.hakemusData.resetCustomerIds()
            val hakemusData = hakemus.applicationData.resetCustomerIds()
            assertThat(response)
                .isNotNull()
                .prop(Taydennys::taydennyspyyntoId)
                .isEqualTo(taydennyspyynto.id)
            assertThat(taydennysData).isEqualTo(hakemusData)
        }

        @Test
        fun `writes the created taydennys to the audit log`() {
            val hakemus = builder().save()
            taydennyspyyntoFactory.save(hakemus.id)
            auditLogRepository.deleteAll()

            val result = taydennysService.create(hakemus.id, USERNAME)

            assertThat(auditLogRepository.findByType(ObjectType.TAYDENNYS)).single().isSuccess(
                Operation.CREATE) {
                    hasUserActor(USERNAME)
                    withTarget {
                        hasTargetType(ObjectType.TAYDENNYS)
                        hasId(result.id)
                        hasNoObjectBefore()
                        hasObjectAfter(result)
                    }
                }
        }

        /**
         * The created entities for customers and contacts are in a different table in the database,
         * so the IDs cannot be the same for the taydennys and hakemus.
         *
         * Otherwise, the data should be identical, so the easiest way to compare them is to reset
         * all the IDs in both of them to a known fixed value and them see if they're equal.
         */
        private fun HakemusData.resetCustomerIds(): HakemusData {
            val customer = customerWithContacts?.resetIds()
            return when (this) {
                is JohtoselvityshakemusData -> {
                    val contractor = contractorWithContacts?.resetIds()
                    val representative = representativeWithContacts?.resetIds()
                    val developer = propertyDeveloperWithContacts?.resetIds()
                    copy(
                        customerWithContacts = customer,
                        contractorWithContacts = contractor,
                        representativeWithContacts = representative,
                        propertyDeveloperWithContacts = developer)
                }
                is KaivuilmoitusData -> {
                    val contractor = contractorWithContacts?.resetIds()
                    val representative = representativeWithContacts?.resetIds()
                    val developer = propertyDeveloperWithContacts?.resetIds()
                    copy(
                        customerWithContacts = customer,
                        contractorWithContacts = contractor,
                        representativeWithContacts = representative,
                        propertyDeveloperWithContacts = developer)
                }
            }
        }

        private fun Hakemusyhteystieto.resetIds() =
            copy(id = fixedUUID, yhteyshenkilot = yhteyshenkilot.map { it.copy(id = fixedUUID) })
    }
}
