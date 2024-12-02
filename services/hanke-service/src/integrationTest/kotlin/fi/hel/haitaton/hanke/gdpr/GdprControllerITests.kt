package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.test.USERNAME
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

private const val DELETE_SCOPE = "gdprdelete"
private const val QUERY_SCOPE = "gdprquery"

@WebMvcTest(controllers = [GdprController::class], properties = ["haitaton.gdpr.disabled=false"])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
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
            get { subject("Other user") }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When scope is incorrect, return 401`() {
            get { scopes("allu.gdprquery", DELETE_SCOPE) }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When scope structure is incorrect, return 401`() {
            get {
                    claims { it.clear() }
                    subject(USERNAME)
                    claim("authorization", listOf(mapOf("scopes" to listOf(QUERY_SCOPE))))
                }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When user has no info, return 204`() {
            every { gdprService.findGdprInfo(USERNAME) }.returns(null)

            get()
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verify { gdprService.findGdprInfo(USERNAME) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When there's an internal error, return gdpr error response`() {
            every { gdprService.findGdprInfo(USERNAME) }.throws(RuntimeException())
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
              }"""

            get()
                .andExpect(MockMvcResultMatchers.status().`is`(500))
                .andExpect(content().json(expectedError))

            verify { gdprService.findGdprInfo(USERNAME) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `When there are applications, return json response`() {
            val info =
                CollectionNode(
                    "user",
                    listOf(
                        StringNode("id", USERNAME),
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
            every { gdprService.findGdprInfo(USERNAME) }.returns(info)
            val expectedResponse =
                """
              {
                "key":"user",
                "children": [
                  {
                    "key":"id",
                    "value":"$USERNAME"
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
            """

            get()
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(content().json(expectedResponse))

            verifySequence {
                gdprService.findGdprInfo(USERNAME)
                disclosureLogService.saveForProfiili(any<CollectionNode>(), USERNAME)
            }
        }
    }

    @Nested
    inner class DeleteUserInformation {

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `When subject does not match user id, return 401`(dryRun: Boolean) {
            delete(dryRun) { subject("Other user") }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `When scope is incorrect, return 401`(dryRun: Boolean) {
            delete(dryRun) { scopes(QUERY_SCOPE, "allu.gdprdelete") }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `When scope structure is incorrect, return 401`(dryRun: Boolean) {
            delete(dryRun) {
                    claims { claims ->
                        claims.clear()
                        claims["Wrong field"] = DELETE_SCOPE
                    }
                    subject(USERNAME)
                }
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(content().string(""))

            verify { gdprService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `Returns 204 when any found information was removed`() {
            every { gdprService.canDelete(USERNAME) } returns true

            delete()
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verifySequence {
                gdprService.canDelete(USERNAME)
                gdprService.deleteInfo(USERNAME)
            }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `Doesn't remove anything if doing a dry run`() {
            every { gdprService.canDelete(USERNAME) } returns true

            delete(true)
                .andExpect(MockMvcResultMatchers.status().isNoContent)
                .andExpect(content().string(""))

            verifyAll { gdprService.canDelete(USERNAME) }
            verify { disclosureLogService wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} dryRun={0}")
        @ValueSource(booleans = [true, false])
        fun `Returns 403 and reasons if applications in handling`(dryRun: Boolean) {
            val gdprErrors = listOf("JS1", "JS2").map { GdprError.fromSentApplication(it) }
            every { gdprService.canDelete(USERNAME) } throws DeleteForbiddenException(gdprErrors)
            val expectedResponse =
                """
                {
                  "errors": [
                    {
                      "code": "HAI2003",
                      "message": {
                        "fi": "Keskeneräinen hakemus tunnuksella JS1. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "en": "An unfinished application with the ID JS1. Please contact alueidenkaytto@hel.fi to remove the application.",
                        "sv": "Pågående ansökan med koden JS1. Kontakta alueidenkaytto@hel.fi för att ta bort ansökan."
                      }
                    },
                    {
                      "code": "HAI2003",
                      "message": {
                        "fi": "Keskeneräinen hakemus tunnuksella JS2. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                        "en": "An unfinished application with the ID JS2. Please contact alueidenkaytto@hel.fi to remove the application.",
                        "sv": "Pågående ansökan med koden JS2. Kontakta alueidenkaytto@hel.fi för att ta bort ansökan."
                      }
                    }
                  ]
                }
            """

            delete(dryRun)
                .andExpect(MockMvcResultMatchers.status().isForbidden)
                .andExpect(content().json(expectedResponse))

            verifyAll { gdprService.canDelete(USERNAME) }
            verify { disclosureLogService wasNot Called }
        }
    }

    private fun get(jwtModifier: Jwt.Builder.() -> Unit = {}): ResultActions {
        val jwtBuilder = jwtBuilder.scopes(QUERY_SCOPE)
        jwtBuilder.jwtModifier()
        return mockMvc.perform(
            MockMvcRequestBuilders.get("/gdpr-api/$USERNAME")
                .accept(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(jwtBuilder.build())))
    }

    private fun delete(
        dryRun: Boolean = false,
        jwtModifier: Jwt.Builder.() -> Unit = {},
    ): ResultActions {
        val url = if (dryRun) "/gdpr-api/$USERNAME?dry_run=true" else "/gdpr-api/$USERNAME"
        val jwtBuilder = jwtBuilder.scopes(DELETE_SCOPE)
        jwtBuilder.jwtModifier()
        return mockMvc.perform(
            MockMvcRequestBuilders.delete(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(jwtBuilder.build())))
    }

    private fun Jwt.Builder.scopes(vararg scopes: String): Jwt.Builder =
        claim("authorization", mapOf("permissions" to listOf(mapOf("scopes" to scopes.toList()))))

    private val jwtBuilder: Jwt.Builder =
        Jwt.withTokenValue("token").header("alg", "none").subject(USERNAME)
}
