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
import assertk.assertions.prop
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_PHONE
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TESTIHENKILO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["haitaton.features.user-management=false"])
class OldGdprServiceITest : IntegrationTest() {

    @Autowired lateinit var gdprService: OldGdprService
    @Autowired lateinit var applicationFactory: ApplicationFactory
    @Autowired lateinit var hankeFactory: HankeFactory
    @Autowired lateinit var applicationRepository: ApplicationRepository

    @Nested
    inner class FindGdprInfo {
        @Test
        fun `Returns null without applications`() {
            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `Returns null when user has no applications`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            applicationFactory.saveApplicationEntities(3, "Other User", hanke)
            val result = gdprService.findGdprInfo(USERNAME)

            assertThat(result).isNull()
        }

        @Test
        fun `Returns information for user's applications`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            applicationFactory.saveApplicationEntities(6, USERNAME, hanke) { i, application ->
                if (i % 2 == 0) {
                    application.userId = "Other User"
                    application.setOrdererName("Other", "User")
                } else {
                    application.userId = USERNAME
                    application.setOrdererName(TEPPO, TESTIHENKILO)
                }
            }

            val result = gdprService.findGdprInfo(USERNAME)

            val expected =
                CollectionNode(
                    "user",
                    listOf(
                        StringNode("id", USERNAME),
                        StringNode("etunimi", TEPPO),
                        StringNode("sukunimi", TESTIHENKILO),
                        StringNode("puhelinnumero", TEPPO_PHONE),
                        StringNode("sahkoposti", TEPPO_EMAIL),
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
                ApplicationFactory.createCompanyCustomer()
                    .withContacts(
                        ApplicationFactory.createContact(firstName, lastName, orderer = true)
                    )
            )
        }
    }

    @Nested
    inner class FindApplicationsToDeletes {
        @Test
        fun `Returns empty list with no applications`() {
            val result = gdprService.findApplicationsToDelete(USERNAME)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns empty list with no matching applications`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            applicationFactory.saveApplicationEntities(3, "Other user", hanke)

            val result = gdprService.findApplicationsToDelete(USERNAME)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns applications when only pending applications`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            val statuses = listOf(null, ApplicationStatus.PENDING, ApplicationStatus.PENDING_CLIENT)
            val applications =
                applicationFactory.saveApplicationEntities(3, USERNAME, hanke) { i, application ->
                    application.alluStatus = statuses[i]
                }

            val result = gdprService.findApplicationsToDelete(USERNAME)

            assertThat(result).hasSize(3)
            assertThat(result).extracting(Application::alluStatus).hasSameElementsAs(statuses)
            assertThat(result)
                .extracting(Application::id)
                .hasSameElementsAs(applications.map { it.id })
        }

        @Test
        fun `Throws exception when some applications are not pending`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            val statuses =
                listOf(
                    ApplicationStatus.HANDLING,
                    ApplicationStatus.PENDING,
                    ApplicationStatus.DECISION
                )
            applicationFactory.saveApplicationEntities(3, USERNAME, hanke) { i, application ->
                application.alluStatus = statuses[i]
                application.applicationIdentifier = "HAI-00$i"
            }

            val exception =
                assertThrows<DeleteForbiddenException> {
                    gdprService.findApplicationsToDelete(USERNAME)
                }

            assertThat(exception.errors).all {
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
        fun `Deletes all given applications of the given user`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            applicationFactory
                .saveApplicationEntities(6, USERNAME, hanke) { i, application ->
                    if (i % 2 == 0) application.userId = "Other User"
                }
                .map { it.toApplication() }

            gdprService.deleteInfo(USERNAME)

            val remainingApplications = applicationRepository.findAll()
            assertThat(remainingApplications).hasSize(3)
            assertThat(remainingApplications).each {
                it.prop(ApplicationEntity::userId).isEqualTo("Other User")
            }
        }
    }
}
