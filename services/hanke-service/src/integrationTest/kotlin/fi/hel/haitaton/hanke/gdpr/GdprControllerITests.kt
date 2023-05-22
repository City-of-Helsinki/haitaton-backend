package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyAll
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content

private const val USERID = "test-user"

@WebMvcTest(controllers = [GdprController::class], properties = ["haitaton.gdpr.disabled=false"])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERID)
class GdprControllerITests(@Autowired var mockMvc: MockMvc) {

    @Autowired lateinit var gdprService: GdprService

    @Autowired lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(gdprService, disclosureLogService)
    }

    @Nested
    inner class GetByUserId {

        @Test
        fun `When subject does not match user id, return 401`() {
            get { it.subject("Other user") }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When scope is incorrect, return 401`() {
            get {
                    it.claim(
                        "http://localhost:8080",
                        listOf("allu.gdprquery", "haitaton.gdprdelete")
                    )
                }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When scope field is incorrect, return 401`() {
            get {
                    it.claims { claims ->
                        claims.clear()
                        claims["Wrong field"] = "haitaton.gdprquery"
                    }
                }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When user has no info, return 204`() {
            every { gdprService.findGdprInfo(USERID) }.returns(null)

            get()
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verify { gdprService.findGdprInfo(USERID) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When there's an internal error, return gdpr error response`() {
            every { gdprService.findGdprInfo(USERID) }.throws(RuntimeException())
            val expectedError =
                """
              {
                "errors": [
                  {
                    "code": "HAI0002",
                    "message": {
                      "fi": "Tapahtui virhe",
                      "sv": "Det inträffade ett fel",
                      "en": "An error occurred"
                    }
                  }
                ]
              }""".trimIndent()

            get()
                .andExpect(MockMvcResultMatchers.status().`is`(500))
                .andExpect(content().json(expectedError))

            verify { gdprService.findGdprInfo(USERID) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When there are applications, return json response`() {
            val info =
                CollectionNode(
                    "user",
                    listOf(
                        StringNode("id", USERID),
                        StringNode("nimi", "Teppo Testihenkilö"),
                        CollectionNode(
                            "organisaatio",
                            listOf(
                                IntNode("id", 4412),
                                StringNode("nimi", "Dna"),
                                StringNode("tunnus", "3766028-0"),
                            ),
                        ),
                    ),
                )
            every { gdprService.findGdprInfo(USERID) }.returns(info)
            val expectedResponse =
                """
              {
                "key":"user",
                "children": [
                  {
                    "key":"id",
                    "value":"test-user"
                  },
                  {
                    "key":"nimi",
                    "value":"Teppo Testihenkilö"
                  },
                  {
                    "key":"organisaatio",
                    "children":[
                      {
                        "key":"id",
                        "value":4412
                      },
                      {
                        "key":"nimi",
                        "value":"Dna"
                      },
                      {
                        "key":"tunnus",
                        "value":"3766028-0"
                      }
                    ]
                  }
                ]
              }
            """.trimIndent()

            get()
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(content().json(expectedResponse))

            verifySequence {
                gdprService.findGdprInfo(USERID)
                disclosureLogService.saveDisclosureLogsForProfiili(USERID, any<CollectionNode>())
            }
        }
    }

    @Nested
    inner class DeleteUserInformation {

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `When subject does not match user id, return 401`(dryRun: Boolean) {
            delete(dryRun) { it.subject("Other user") }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `When scope is incorrect, return 401`(dryRun: Boolean) {
            delete(dryRun) {
                    it.claim(
                        "http://localhost:8080",
                        listOf("haitaton.gdprquery", "allu.gdprdelete")
                    )
                }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `When scope field is incorrect, return 401`(dryRun: Boolean) {
            delete(dryRun) {
                    it.claims { claims ->
                        claims.clear()
                        claims["Wrong field"] = "haitaton.gdprdelete"
                    }
                }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `Returns 204 when no information was found`() {
            every { gdprService.findApplicationsToDelete(USERID) } returns listOf()

            delete()
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verifySequence {
                gdprService.findApplicationsToDelete(USERID)
                gdprService.deleteApplications(listOf(), USERID)
            }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `Returns 204 when information was found and deleted`() {
            val applications = AlluDataFactory.createApplications(3)
            every { gdprService.findApplicationsToDelete(USERID) } returns applications

            delete()
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verifySequence {
                gdprService.findApplicationsToDelete(USERID)
                gdprService.deleteApplications(applications, USERID)
            }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `Doesn't remove anything if doing a dry run`() {
            val applications = AlluDataFactory.createApplications(3)
            every { gdprService.findApplicationsToDelete(USERID) } returns applications

            delete(true)
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verifyAll { gdprService.findApplicationsToDelete(USERID) }
            verify { disclosureLogService wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `Returns 403 and reasons if applications in handling`(dryRun: Boolean) {
            val applications =
                AlluDataFactory.createApplications(2) { i, application ->
                    application.copy(applicationIdentifier = "JS$i")
                }
            every { gdprService.findApplicationsToDelete(USERID) } throws
                DeleteForbiddenException(applications)
            val expectedResponse =
                """
                {
                  "errors": [
                    {
                      "code": "HAI2003",
                      "message": {
                        "fi": "Keskeneräinen hakemus tunnuksella JS1. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "en": "en: Keskeneräinen hakemus tunnuksella JS1. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "sv": "sv: Keskeneräinen hakemus tunnuksella JS1. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi."
                      }
                    },
                    {
                      "code": "HAI2003",
                      "message": {
                        "fi": "Keskeneräinen hakemus tunnuksella JS2. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "en": "en: Keskeneräinen hakemus tunnuksella JS2. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "sv": "sv: Keskeneräinen hakemus tunnuksella JS2. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi."
                      }
                    }
                  ]
                }
            """.trimIndent()

            delete(dryRun)
                .andExpect(MockMvcResultMatchers.status().isForbidden)
                .andExpect(content().json(expectedResponse))

            verifyAll { gdprService.findApplicationsToDelete(USERID) }
            verify { disclosureLogService wasNot Called }
        }
    }

    private fun get(jwtModifier: (Jwt.Builder) -> Unit = {}): ResultActions {
        val jwtBuilder = jwtBuilder.claim("http://localhost:8080", listOf("haitaton.gdprquery"))
        jwtModifier(jwtBuilder)
        return mockMvc.perform(
            MockMvcRequestBuilders.get("/gdpr-api/$USERID")
                .accept(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(jwtBuilder.build()))
        )
    }

    private fun delete(
        dryRun: Boolean = false,
        jwtModifier: (Jwt.Builder) -> Unit = {},
    ): ResultActions {
        val url = if (dryRun) "/gdpr-api/$USERID?dry_run=true" else "/gdpr-api/$USERID"
        val jwtBuilder = jwtBuilder.claim("http://localhost:8080", listOf("haitaton.gdprdelete"))
        jwtModifier(jwtBuilder)
        return mockMvc.perform(
            MockMvcRequestBuilders.delete(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(jwtBuilder.build()))
        )
    }

    private val jwtBuilder: Jwt.Builder =
        Jwt.withTokenValue("token").header("alg", "none").claim("sub", USERID)
}
