package fi.hel.haitaton.hanke.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.andReturnContent
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withCustomerContacts
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT_APPLICATIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.validation.InvalidApplicationDataException
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifySequence
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val HANKE_TUNNUS = "HAI-1234"
private const val BASE_URL = "/hakemukset"
private const val HANKKEET_BASE_URL = "/hankkeet"

@WebMvcTest(
    controllers = [ApplicationController::class],
    properties = ["haitaton.features.user-management=false"]
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ApplicationControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var authorizer: ApplicationAuthorizer
    @Autowired private lateinit var disclosureLogService: DisclosureLogService
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val id = 1234L

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(applicationService, hankeService, authorizer)
    }

    @Nested
    inner class GetHankeHakemukset {
        private val url = "$HANKKEET_BASE_URL/$HANKE_TUNNUS/hakemukset"

        @Test
        @WithAnonymousUser
        fun `Without authenticated user return unauthorized (401) `() {
            get(url)
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `With unknown hanke tunnus return 404`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true
            every { hankeService.getHankeApplications(HANKE_TUNNUS) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                hankeService.getHankeApplications(HANKE_TUNNUS)
            }
        }

        @Test
        fun `When user does not have permission return 404`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) }
        }

        @Test
        fun `With no applications return empty list`() {
            every { hankeService.getHankeApplications(HANKE_TUNNUS) } returns listOf()
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true

            val response: ApplicationsResponse = get(url).andExpect(status().isOk).andReturnBody()

            Assertions.assertTrue(response.applications.isEmpty())
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                hankeService.getHankeApplications(HANKE_TUNNUS)
            }
        }

        @Test
        fun `With known hanketunnus return applications`() {
            val applications = ApplicationFactory.createApplications(5)
            every { hankeService.getHankeApplications(HANKE_TUNNUS) } returns applications
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true

            val response: ApplicationsResponse = get(url).andExpect(status().isOk).andReturnBody()

            Assertions.assertTrue(response.applications.isNotEmpty())
            assertEquals(ApplicationsResponse(applications), response)
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                hankeService.getHankeApplications(HANKE_TUNNUS)
                disclosureLogService.saveDisclosureLogsForApplications(applications, USERNAME)
            }
        }
    }

    @Nested
    inner class GetAllApplicationsForUser {
        @Test
        @WithAnonymousUser
        fun `getAll without user ID returns 401`() {
            get(BASE_URL).andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when no accessible applications should return empty list`() {
            every { applicationService.getAllApplicationsForUser(USERNAME) } returns listOf()

            get(BASE_URL).andExpect(status().isOk).andExpect(content().json("[]"))

            verify { applicationService.getAllApplicationsForUser(USERNAME) }
        }

        @Test
        fun `when applications exist should return applications for current user`() {
            every { applicationService.getAllApplicationsForUser(USERNAME) } returns
                ApplicationFactory.createApplications(3)

            get(BASE_URL)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(3))

            verify { applicationService.getAllApplicationsForUser(USERNAME) }
        }
    }

    @Nested
    inner class GetApplicationById {
        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            get("$BASE_URL/$id").andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when application does not exist should return 404`() {
            every { authorizer.authorizeApplicationId(id, VIEW.name) } throws
                ApplicationNotFoundException(id)

            get("$BASE_URL/$id").andExpect(status().isNotFound)

            verify { authorizer.authorizeApplicationId(id, VIEW.name) }
        }

        @Test
        fun `when application exists should return it`() {
            every { authorizer.authorizeApplicationId(id, VIEW.name) } returns true
            every { applicationService.getApplicationById(id) } returns
                ApplicationFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)

            get("$BASE_URL/$id")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applicationType").value("CABLE_REPORT"))
                .andExpect(jsonPath("$.applicationData.applicationType").value("CABLE_REPORT"))

            verify {
                authorizer.authorizeApplicationId(id, VIEW.name)
                applicationService.getApplicationById(id)
            }
        }

        @Test
        fun `when application belongs to a hanke should return application with correct hankeTunnus`() {
            every { authorizer.authorizeApplicationId(id, VIEW.name) } returns true
            every { applicationService.getApplicationById(id) } returns
                ApplicationFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)

            get("$BASE_URL/$id")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))

            verify {
                authorizer.authorizeApplicationId(id, VIEW.name)
                applicationService.getApplicationById(id)
            }
        }
    }

    @Nested
    inner class CreateApplication {
        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            post(BASE_URL, ApplicationFactory.createApplication(id = 0))
                .andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when no request body should return 400`() {
            post(BASE_URL).andExpect(status().isBadRequest)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when valid application should create application`() {
            val newApplication =
                ApplicationFactory.createApplication(id = 0, hankeTunnus = HANKE_TUNNUS)
            val createdApplication = newApplication.copy(id = 1234)
            every { authorizer.authorizeCreate(newApplication) } returns true
            every { applicationService.create(newApplication, USERNAME) } returns createdApplication

            val response: Application =
                post(BASE_URL, newApplication).andExpect(status().isOk).andReturnBody()

            assertEquals(createdApplication, response)
            verifySequence {
                authorizer.authorizeCreate(newApplication)
                applicationService.create(newApplication, USERNAME)
            }
        }

        @Test
        fun `when missing application data type should return 400`() {
            val application = ApplicationFactory.createApplication(id = 0)
            val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
            (content.get("applicationData") as ObjectNode).remove("applicationType")

            postRaw(BASE_URL, content.toJsonString()).andExpect(status().isBadRequest)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when end before start should return 400`() {
            val application =
                ApplicationFactory.createApplication(
                    id = 0,
                    applicationData =
                        ApplicationFactory.createCableReportApplicationData(
                            startTime = ZonedDateTime.now(),
                            endTime = ZonedDateTime.now().minusDays(1)
                        )
                )
            every { authorizer.authorizeCreate(any<Application>()) } returns true

            post(BASE_URL, application).andExpect(status().isBadRequest)

            verify {
                authorizer.authorizeCreate(any<Application>())
                applicationService wasNot Called
            }
        }

        @Test
        fun `when missing application type should return 400`() {
            val application = ApplicationFactory.createApplication(id = 0)
            val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
            content.remove("applicationType")

            postRaw(BASE_URL, content.toJsonString())
                .andDo { print(it) }
                .andExpect(status().isBadRequest)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when invalid y-tunnus should return 400`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer(registryKey = "281192-937W")
                            .withContacts()
                )
            val application =
                ApplicationFactory.createApplication(id = 0, applicationData = applicationData)
            every { authorizer.authorizeCreate(application) } returns true

            post(BASE_URL, application).andExpect(status().isBadRequest)

            verifySequence {
                authorizer.authorizeCreate(application)
                applicationService wasNot Called
            }
        }

        @Test
        fun `when application without hankeTunnus should return 400`() {
            val newApplication =
                ApplicationFactory.createApplication(id = 0, hankeTunnus = HANKE_TUNNUS)
            val json = objectMapper.valueToTree<ObjectNode>(newApplication)
            json.remove("hankeTunnus")
            val text = json.asText()
            postRaw(BASE_URL, text).andExpect(status().isBadRequest)
        }

        @Test
        fun `when no hanke permission should return hanke not found 404`() {
            val newApplication =
                ApplicationFactory.createApplication(id = 0, hankeTunnus = HANKE_TUNNUS)
            every { authorizer.authorizeCreate(newApplication) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            post(BASE_URL, newApplication).andExpect(status().isNotFound)

            verify { authorizer.authorizeCreate(newApplication) }
        }
    }

    @Nested
    inner class CreateApplicationWithGeneratedHanke {
        @Test
        fun `when valid request should succeed and return 200`() {
            val applicationInput = ApplicationFactory.cableReportWithoutHanke()
            val mockCreatedApplication = applicationInput.toNewApplication(HANKE_TUNNUS)
            every { hankeService.generateHankeWithApplication(applicationInput, any()) } returns
                mockCreatedApplication

            val response: Application =
                post("$BASE_URL/johtoselvitys", applicationInput)
                    .andExpect(status().isOk)
                    .andReturnBody()

            assertEquals(response, mockCreatedApplication)
            verify { hankeService.generateHankeWithApplication(applicationInput, any()) }
        }

        @Test
        fun `when invalid data should fail validation and return 400`() {
            val applicationInput =
                ApplicationFactory.createApplication()
                    .withCustomerContacts(
                        ApplicationFactory.createContact(orderer = true),
                        ApplicationFactory.createContact(orderer = true)
                    )
                    .toCableReportWithoutHanke()

            val response =
                post("$BASE_URL/johtoselvitys", applicationInput)
                    .andExpect(status().isBadRequest)
                    .andReturnContent()

            assertThat(response)
                .isEqualTo(
                    HankeErrorDetail(
                            HankeError.HAI2008,
                            listOf("customersWithContacts[].contacts[].orderer")
                        )
                        .toJsonString()
                )
            verify { hankeService wasNot Called }
        }
    }

    @Nested
    inner class UpdateApplication {
        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            put("$BASE_URL/$id", ApplicationFactory.createApplication())
                .andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when no request body should return 400`() {
            put("$BASE_URL/$id").andExpect(status().isBadRequest)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when end before start should return 400`() {
            val application =
                ApplicationFactory.createApplication(
                    id = 0,
                    applicationData =
                        ApplicationFactory.createCableReportApplicationData(
                            startTime = ZonedDateTime.now(),
                            endTime = ZonedDateTime.now().minusDays(1)
                        )
                )
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true

            val response =
                put("$BASE_URL/$id", application)
                    .andExpect(status().isBadRequest)
                    .andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, listOf("endTime")).toJsonString())
            verify {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService wasNot Called
            }
        }

        @Test
        fun `when invalid y-tunnus should return 400`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer(registryKey = "281192-937W")
                            .withContacts()
                )
            val application =
                ApplicationFactory.createApplication(id = 0, applicationData = applicationData)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true

            put("$BASE_URL/$id", application).andExpect(status().isBadRequest)

            verify {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService wasNot Called
            }
        }

        @Test
        fun `when application exists should return application`() {
            val application = ApplicationFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every {
                applicationService.updateApplicationData(id, application.applicationData, USERNAME)
            } returns application

            val response: Application =
                put("$BASE_URL/$id", application).andExpect(status().isOk).andReturnBody()

            assertEquals(application, response)
            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.updateApplicationData(id, application.applicationData, USERNAME)
            }
        }

        @Test
        fun `when missing application data type should return 400`() {
            val application = ApplicationFactory.createApplication()
            val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
            (content.get("applicationData") as ObjectNode).remove("applicationType")

            putRaw("$BASE_URL/$id", content.toJsonString()).andExpect(status().isBadRequest)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when missing application type should return 400`() {
            val application = ApplicationFactory.createApplication()
            val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
            content.remove("applicationType")

            putRaw("$BASE_URL/$id", content.toJsonString())
                .andDo { print(it) }
                .andExpect(status().isBadRequest)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when missing required data should return 400`() {
            val mockErrorPaths = listOf("startTime", "customerWithContacts.customer.type")
            val application = ApplicationFactory.createApplication()
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every {
                applicationService.updateApplicationData(id, application.applicationData, USERNAME)
            } throws InvalidApplicationDataException(mockErrorPaths)

            val response =
                put("$BASE_URL/$id", application)
                    .andExpect(status().isBadRequest)
                    .andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, mockErrorPaths).toJsonString())
            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.updateApplicationData(id, application.applicationData, USERNAME)
            }
        }

        @Test
        fun `when no application should return 404`() {
            val application = ApplicationFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } throws
                ApplicationNotFoundException(id)

            put("$BASE_URL/$id", application).andExpect(status().isNotFound)

            verify { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) }
        }

        @Test
        fun `when application sent to Allu should return 409`() {
            val application = ApplicationFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every {
                applicationService.updateApplicationData(id, application.applicationData, USERNAME)
            } throws ApplicationAlreadySentException(id, 21, ApplicationStatus.HANDLING)

            put("$BASE_URL/$id", application)
                .andExpect(status().isConflict)
                .andExpect(jsonPath("errorCode").value("HAI2009"))

            verify {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.updateApplicationData(id, application.applicationData, USERNAME)
            }
        }
    }

    @Nested
    inner class SendApplication {
        @Test
        @WithAnonymousUser
        fun `when unknown user should 401`() {
            post("$BASE_URL/$id/send-application").andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when no request body should send application to Allu and return result`() {
            val application = ApplicationFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.sendApplication(id, USERNAME) } returns application

            val response: Application =
                post("$BASE_URL/$id/send-application").andExpect(status().isOk).andReturnBody()

            assertEquals(application, response)
            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.sendApplication(id, USERNAME)
            }
        }

        @Test
        fun `when request body is present should ignore it`() {
            val application = ApplicationFactory.createApplication(id = id, alluid = 21)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.sendApplication(id, USERNAME) } returns application

            val response: Application =
                post("$BASE_URL/$id/send-application", application.copy(alluid = 9999))
                    .andExpect(status().isOk)
                    .andReturnBody()

            assertEquals(application, response)
            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.sendApplication(id, USERNAME)
            }
        }

        @Test
        fun `when broken application body should ignore request body`() {
            val application = ApplicationFactory.createApplication()
            val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
            (content.get("applicationData") as ObjectNode).remove("applicationType")
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.sendApplication(id, USERNAME) } returns application

            val response: Application =
                postRaw("$BASE_URL/$id/send-application", content.toJsonString())
                    .andExpect(status().isOk)
                    .andReturnBody()

            assertEquals(application, response)
            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.sendApplication(id, USERNAME)
            }
        }

        @Test
        fun `when no application or permission should return 404`() {
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } throws
                ApplicationNotFoundException(id)

            post("$BASE_URL/$id/send-application").andExpect(status().isNotFound)

            verify { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) }
        }

        @Test
        fun `when application no longer pending should return 409`() {
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.sendApplication(id, USERNAME) } throws
                ApplicationAlreadyProcessingException(id, 21)

            post("$BASE_URL/$id/send-application").andExpect(status().isConflict)

            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.sendApplication(id, USERNAME)
            }
        }

        @Test
        fun `when invalid application data should return 409`() {
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.sendApplication(id, USERNAME) } throws
                AlluDataException("applicationData.some.path", AlluDataError.EMPTY_OR_NULL)

            post("$BASE_URL/$id/send-application").andExpect(status().isConflict)

            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.sendApplication(id, USERNAME)
            }
        }

        @Test
        fun `when missing data in application should return 400 with details`() {
            val mockErrorPaths = listOf("startTime", "customerWithContacts.customer.type")
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.sendApplication(id, USERNAME) } throws
                InvalidApplicationDataException(mockErrorPaths)

            val response =
                post("$BASE_URL/$id/send-application")
                    .andExpect(status().isBadRequest)
                    .andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, mockErrorPaths).toJsonString())
            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.sendApplication(id, USERNAME)
            }
        }
    }

    @Nested
    inner class DeleteApplication {
        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            delete("$BASE_URL/$id").andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when no application or no permission should return 404`() {
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } throws
                ApplicationNotFoundException(id)

            delete("$BASE_URL/$id").andExpect(status().isNotFound)

            verify { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) }
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `when application exists should delete application and return deletion result`(
            hankeDeleted: Boolean
        ) {
            val expectedResponseBody = ApplicationDeletionResultDto(hankeDeleted)
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME) } returns
                expectedResponseBody

            delete("$BASE_URL/$id")
                .andExpect(status().isOk)
                .andExpect(content().json(expectedResponseBody.toJsonString()))

            verifySequence {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME)
            }
        }

        @Test
        fun `when non-pending application in allu should returns 409`() {
            every { authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name) } returns true
            every { applicationService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME) } throws
                ApplicationAlreadyProcessingException(id, 41)

            delete("$BASE_URL/$id").andExpect(status().isConflict)

            verify {
                authorizer.authorizeApplicationId(id, EDIT_APPLICATIONS.name)
                applicationService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME)
            }
        }
    }

    @Nested
    inner class DownloadDecision {
        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            get("$BASE_URL/$id/paatos").andExpect(status().isUnauthorized)

            verify { applicationService wasNot Called }
        }

        @Test
        fun `when no application should return 404`() {
            every { authorizer.authorizeApplicationId(id, VIEW.name) } throws
                ApplicationNotFoundException(id)

            get("$BASE_URL/$id/paatos")
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("errorCode").value("HAI2001"))
                .andExpect(jsonPath("errorMessage").value("Application not found"))

            verify { authorizer.authorizeApplicationId(id, VIEW.name) }
        }

        @Test
        fun `when application has no decision should return 404`() {
            every { authorizer.authorizeApplicationId(id, VIEW.name) } returns true
            every { applicationService.downloadDecision(id, USERNAME) } throws
                ApplicationDecisionNotFoundException("Decision not found in Allu. alluid=23")

            get("$BASE_URL/$id/paatos")
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("errorCode").value("HAI2006"))
                .andExpect(jsonPath("errorMessage").value("Application decision not found"))

            verifySequence {
                authorizer.authorizeApplicationId(id, VIEW.name)
                applicationService.downloadDecision(id, USERNAME)
            }
        }

        @Test
        fun `when decision exists should return bytes and correct headers`() {
            val applicationIdentifier = "JS230001"
            val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
            every { authorizer.authorizeApplicationId(id, VIEW.name) } returns true
            every { applicationService.downloadDecision(id, USERNAME) } returns
                Pair(applicationIdentifier, pdfBytes)
            every { applicationService.getApplicationById(id) } returns
                ApplicationFactory.createApplication(applicationIdentifier = applicationIdentifier)

            get("$BASE_URL/$id/paatos", APPLICATION_PDF)
                .andExpect(status().isOk)
                .andExpect(header().string("Content-Disposition", "inline; filename=JS230001.pdf"))
                .andExpect(content().bytes(pdfBytes))

            verifySequence {
                authorizer.authorizeApplicationId(id, VIEW.name)
                applicationService.downloadDecision(id, USERNAME)
                applicationService.getApplicationById(id)
            }
        }

        @Test
        fun `when decision exists should write access to audit log`() {
            val applicationIdentifier = "JS230001"
            val application =
                ApplicationFactory.createApplication(applicationIdentifier = applicationIdentifier)
            val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
            every { authorizer.authorizeApplicationId(id, VIEW.name) } returns true
            every { applicationService.downloadDecision(id, USERNAME) } returns
                Pair(applicationIdentifier, pdfBytes)
            every { applicationService.getApplicationById(id) } returns application

            get("$BASE_URL/$id/paatos", APPLICATION_PDF)
                .andExpect(status().isOk)
                .andExpect(header().string("Content-Disposition", "inline; filename=JS230001.pdf"))
                .andExpect(content().bytes(pdfBytes))

            verifySequence {
                authorizer.authorizeApplicationId(id, VIEW.name)
                applicationService.downloadDecision(id, USERNAME)
                applicationService.getApplicationById(id)
                disclosureLogService.saveDisclosureLogsForCableReport(
                    application.toMetadata(),
                    USERNAME
                )
            }
        }

        @Test
        fun `when no hanke permission should return 404`() {
            every { authorizer.authorizeApplicationId(id, VIEW.name) } throws
                ApplicationNotFoundException(id)

            get("$BASE_URL/$id/paatos").andExpect(status().isNotFound)

            verify { authorizer.authorizeApplicationId(id, VIEW.name) }
        }
    }

    private fun Application.toCableReportWithoutHanke(): CableReportWithoutHanke =
        CableReportWithoutHanke(
            applicationType = applicationType,
            applicationData = applicationData as CableReportApplicationData
        )
}
