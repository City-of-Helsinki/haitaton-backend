package fi.hel.haitaton.hanke.gdpr

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.hasSameElementsAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERID = "test-user"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@WithMockUser(USERID)
class GdprServiceITest : DatabaseTest() {

    @Autowired lateinit var gdprService: GdprService
    @Autowired lateinit var alluDataFactory: AlluDataFactory
    @Autowired lateinit var hankeFactory: HankeFactory
    @Autowired lateinit var applicationRepository: ApplicationRepository

    @Nested
    inner class FindGdprInfo {
        @Test
        fun `Returns null without applications`() {
            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNull()
        }

        @Test
        fun `Returns null when user has no applications`() {
            val hanke = hankeFactory.saveEntity()
            alluDataFactory.saveApplicationEntities(3, "Other User", hanke)
            val result = gdprService.findGdprInfo(USERID)

            assertThat(result).isNull()
        }

        @Test
        fun `Returns information for user's applications`() {
            val hanke = hankeFactory.saveEntity()
            alluDataFactory.saveApplicationEntities(6, USERID, hanke) { i, application ->
                if (i % 2 == 0) {
                    application.userId = "Other User"
                    application.setOrdererName("Other", "User")
                } else {
                    application.userId = USERID
                    application.setOrdererName("Teppo", "Testihenkilö")
                }
            }

            val result = gdprService.findGdprInfo(USERID)

            val expected =
                CollectionNode(
                    "user",
                    listOf(
                        StringNode("id", USERID),
                        StringNode("nimi", TEPPO_TESTI),
                        StringNode("puhelinnumero", "04012345678"),
                        StringNode("sahkoposti", "teppo@example.test"),
                        CollectionNode(
                            "organisaatio",
                            listOf(
                                StringNode("nimi", "DNA"),
                                StringNode("tunnus", "3766028-0"),
                            )
                        ),
                    )
                )
            assertThat(result).isEqualTo(expected)
        }

        private fun ApplicationEntity.setOrdererName(firstName: String, lastName: String) {
            withCustomer(
                AlluDataFactory.createCompanyCustomer()
                    .withContacts(
                        AlluDataFactory.createContact(firstName, lastName, orderer = true)
                    )
            )
        }
    }

    @Nested
    inner class FindApplicationsToDeletes {
        @Test
        fun `Returns empty list with no applications`() {
            val result = gdprService.findApplicationsToDelete(USERID)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns empty list with no matching applications`() {
            val hanke = hankeFactory.saveEntity()
            alluDataFactory.saveApplicationEntities(3, "Other user", hanke)

            val result = gdprService.findApplicationsToDelete(USERID)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns applications when only pending applications`() {
            val hanke = hankeFactory.saveEntity()
            val statuses = listOf(null, ApplicationStatus.PENDING, ApplicationStatus.PENDING_CLIENT)
            val applications =
                alluDataFactory.saveApplicationEntities(3, USERID, hanke) { i, application ->
                    application.alluStatus = statuses[i]
                }

            val result = gdprService.findApplicationsToDelete(USERID)

            assertThat(result).hasSize(3)
            assertThat(result).extracting(Application::alluStatus).hasSameElementsAs(statuses)
            assertThat(result)
                .extracting(Application::id)
                .hasSameElementsAs(applications.map { it.id })
        }

        @Test
        fun `Throws exception when some applications are not pending`() {
            val hanke = hankeFactory.saveEntity()
            val statuses =
                listOf(
                    ApplicationStatus.HANDLING,
                    ApplicationStatus.PENDING,
                    ApplicationStatus.DECISION
                )
            alluDataFactory.saveApplicationEntities(3, USERID, hanke) { i, application ->
                application.alluStatus = statuses[i]
                application.applicationIdentifier = "HAI-00$i"
            }

            val exception =
                assertThrows<DeleteForbiddenException> {
                    gdprService.findApplicationsToDelete(USERID)
                }

            assertThat(exception.errors()).all {
                hasSize(2)
                extracting(GdprError::code).each { it.isEqualTo("HAI2003") }
                extracting { it.message.fi }
                    .containsExactlyInAnyOrder(
                        "Keskeneräinen hakemus tunnuksella HAI-000. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "Keskeneräinen hakemus tunnuksella HAI-002. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                    )
            }
        }
    }

    @Nested
    inner class DeleteApplications {
        @Test
        fun `Deletes all given applications`() {
            val hanke = hankeFactory.saveEntity()
            val applications =
                alluDataFactory
                    .saveApplicationEntities(6, USERID, hanke) { i, application ->
                        if (i % 2 == 0) application.userId = "Other User"
                    }
                    .map { it.toApplication() }

            gdprService.deleteApplications(applications, USERID)

            val remainingApplications = applicationRepository.findAll()
            assertThat(remainingApplications).isEmpty()
        }
    }
}
