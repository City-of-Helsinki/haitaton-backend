package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.geojson.Crs
import org.geojson.GeometryCollection
import org.geojson.LngLatAlt
import org.geojson.Polygon
import org.junit.jupiter.api.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.ZonedDateTime

class CableReportServiceAlluITests {

    lateinit var mockWebServer: MockWebServer
    lateinit var service: CableReportServiceAllu

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()

        val baseUrl = mockWebServer.url("/").toUrl().toString()
        val properties = AlluProperties(baseUrl, "fake_username", "any_password")
        service = CableReportServiceAllu(WebClient.create(), properties)
    }

    @Test
    fun testCreate() {
        val stubbedBearer = addStubbedLoginResponse()

        val stubbedApplicationId = 1337
        val applicationIdResponse = MockResponse().setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(stubbedApplicationId.toString())

        mockWebServer.enqueue(applicationIdResponse)

        val application = getTestApplication()
        val actualApplicationId = service.create(application)
        assertThat(actualApplicationId).isEqualTo(stubbedApplicationId)

        val loginRequest = mockWebServer.takeRequest()
        assertThat(loginRequest.method).isEqualTo("POST")
        assertThat(loginRequest.path).isEqualTo("/v2/login")

        val request = mockWebServer.takeRequest()

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v2/cablereports")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + stubbedBearer)
    }

    @Test
    fun testCreateErrorHandling() {
        val stubbedBearer = addStubbedLoginResponse()

        val applicationIdResponse = MockResponse().setResponseCode(400)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""[{"errorMessage":"Cable report should have one orderer contact","additionalInfo":"customerWithContacts"}]""")
        mockWebServer.enqueue(applicationIdResponse)

        assertThrows<WebClientResponseException.BadRequest> { service.create(getTestApplication()) }

        val loginRequest = mockWebServer.takeRequest()
        assertThat(loginRequest.method).isEqualTo("POST")
        assertThat(loginRequest.path).isEqualTo("/v2/login")
        assertThat(loginRequest.getHeader("Authorization")).isNull()

        val createRequest = mockWebServer.takeRequest()
        assertThat(createRequest.method).isEqualTo("POST")
        assertThat(createRequest.path).isEqualTo("/v2/cablereports")
        assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer " + stubbedBearer)
    }

    private fun addStubbedLoginResponse(): String {
        val stubbedBearer = "123dynamite-789wohoo"
        val loginResponse = MockResponse().setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(stubbedBearer)
        mockWebServer.enqueue(loginResponse)
        return stubbedBearer
    }

    private fun getTestApplication(): CableReportApplication {
        val customer = Customer(
                type = CustomerType.COMPANY,
                name = "Haitaton Oy Ab",
                country = "FI",
                postalAddress = PostalAddress(
                        streetAddress = StreetAddress("Haittatie 6"),
                        postalCode = "12345",
                        city = "Haitaton City"
                ),
                email = "info@haitaton.fi",
                phone = "042-555-6125",
                registryKey = "101010-FAKE",
                ovt = null,
                invoicingOperator = null,
                sapCustomerNumber = null
        )
        val hannu = Contact(
                name = "Hannu Haitaton",
                postalAddress = PostalAddress(
                        streetAddress = StreetAddress("Haittatie 8"),
                        postalCode = "12345",
                        city = "Haitaton City"
                ),
                email = "hannu@haitaton.fi",
                phone = "042-555-5216",
                orderer = true
        )
        val kerttu = Contact(
                name = "Kerttu Haitaton",
                postalAddress = PostalAddress(
                        streetAddress = StreetAddress("Haittatie 8"),
                        postalCode = "12345",
                        city = "Haitaton City"
                ),
                email = "kerttu@haitaton.fi",
                phone = "042-555-2182",
                orderer = false
        )

        val customerWContacts = CustomerWithContacts(
                customer = customer,
                contacts = listOf(hannu)
        )
        val contractorWContacts = CustomerWithContacts(
                customer = customer,
                contacts = listOf(kerttu)
        )

        val geometry = GeometryCollection()
        geometry.add(Polygon(
                LngLatAlt(25495815.0, 6673160.0),
                LngLatAlt(25495855.0, 6673160.0),
                LngLatAlt(25495855.0, 6673190.0),
                LngLatAlt(25495815.0, 6673160.0)
        ))
        geometry.crs = Crs()
        geometry.crs.properties["name"] = "EPSG:3879"

        val now = ZonedDateTime.now()

        val application = CableReportApplication(
                name = "Haitaton hankkeen nimi",
                customerWithContacts = customerWContacts,
                geometry = geometry,
                startTime = now.plusDays(1L),
                endTime = now.plusDays(22L),
                pendingOnClient = false,
                identificationNumber = "HAI-123",
                clientApplicationKind = "Telekaapelin laittoa",
                workDescription = "Kaivuhommiahan nää tietty",
                contractorWithContacts = contractorWContacts
        )
        application.constructionWork = true

        return application
    }

}
