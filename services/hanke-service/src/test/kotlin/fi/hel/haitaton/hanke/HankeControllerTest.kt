package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.FeatureService
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.NewYhteystieto
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.domain.geometriat
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.toHankeYhteystieto
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor

private const val username = "testuser"

@ExtendWith(SpringExtension::class)
@Import(HankeControllerTest.TestConfiguration::class)
@WithMockUser(username)
class HankeControllerTest {

    @Configuration
    class TestConfiguration {
        // makes validation happen here in unit test as well
        @Bean fun bean(): MethodValidationPostProcessor = MethodValidationPostProcessor()
        @Bean fun hankeService(): HankeService = mockk()

        @Bean fun permissionService(): PermissionService = mockk()

        @Bean fun yhteystietoLoggingService(): DisclosureLogService = mockk(relaxUnitFun = true)

        @Bean fun hankeAuthorizer(): HankeAuthorizer = mockk(relaxUnitFun = true)

        val featureFlags = FeatureFlags(mapOf(Pair(Feature.HANKE_EDITING, true)))
        @Bean fun featureService(): FeatureService = FeatureService(featureFlags)

        @Bean
        fun hankeController(
            hankeService: HankeService,
            permissionService: PermissionService,
            disclosureLogService: DisclosureLogService,
        ): HankeController =
            HankeController(
                hankeService,
                permissionService,
                disclosureLogService,
            )
    }

    private val mockedHankeTunnus = "AFC1234"

    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var hankeController: HankeController
    @Autowired private lateinit var disclosureLogService: DisclosureLogService
    @Autowired private lateinit var hankeAuthorizer: HankeAuthorizer

    @BeforeEach
    fun cleanUp() {
        clearAllMocks()
    }

    @Test
    fun `test that the getHankeByTunnus returns ok`() {
        val hankeId = 1234

        every { permissionService.hasPermission(hankeId, username, PermissionCode.VIEW) }
            .returns(true)
        every { hankeService.loadHanke(mockedHankeTunnus) }
            .returns(
                Hanke(
                    hankeId,
                    mockedHankeTunnus,
                    true,
                    "Mannerheimintien remontti remonttinen",
                    HankeFactory.defaultKuvaus,
                    Vaihe.OHJELMOINTI,
                    null,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    HankeStatus.DRAFT
                )
            )
        every {
            hankeAuthorizer.authorizeHankeTunnus(mockedHankeTunnus, PermissionCode.VIEW)
        } returns true

        val response = hankeController.getHankeByTunnus(mockedHankeTunnus)

        assertThat(response).isNotNull
        assertThat(response.nimi).isNotEmpty
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(username)) }
    }

    @Test
    fun `test when called without parameters then getHankeList returns ok and two items without geometry`() {
        val listOfHanke =
            listOf(
                Hanke(
                    1234,
                    mockedHankeTunnus,
                    true,
                    "Mannerheimintien remontti remonttinen",
                    HankeFactory.defaultKuvaus,
                    Vaihe.OHJELMOINTI,
                    null,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    HankeStatus.DRAFT
                ),
                Hanke(
                    50,
                    "HAME50",
                    true,
                    "Hämeenlinnanväylän uudistus",
                    HankeFactory.defaultKuvaus,
                    Vaihe.SUUNNITTELU,
                    SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                    1,
                    "Paavo",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    HankeStatus.DRAFT
                )
            )
        every { permissionService.getAllowedHankeIds(username, PermissionCode.VIEW) }
            .returns(listOf(1234, 50))
        every { hankeService.loadHankkeetByIds(listOf(1234, 50)) }.returns(listOfHanke)

        val hankeList = hankeController.getHankeList(false)

        assertThat(hankeList[0].nimi).isEqualTo("Mannerheimintien remontti remonttinen")
        assertThat(hankeList[1].nimi).isEqualTo("Hämeenlinnanväylän uudistus")
        assertThat(hankeList[0].alueet.geometriat()).isEmpty()
        assertThat(hankeList[1].alueet.geometriat()).isEmpty()
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(any(), eq(username)) }
    }

    @Test
    fun `test that the updateHanke can be called with hanke data and response will be 200`() {
        val hanketunnus = "id123"
        val partialHanke =
            Hanke(
                id = 123,
                hankeTunnus = hanketunnus,
                nimi = "hankkeen nimi",
                kuvaus = HankeFactory.defaultKuvaus,
                onYKTHanke = false,
                vaihe = Vaihe.SUUNNITTELU,
                suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT
            )

        // mock HankeService response
        every { hankeService.updateHanke(partialHanke) }
            .returns(partialHanke.copy(modifiedBy = username, modifiedAt = getCurrentTimeUTC()))
        every { permissionService.hasPermission(123, username, PermissionCode.EDIT) }.returns(true)

        // Actual call
        val response: Hanke = hankeController.updateHanke(partialHanke, hanketunnus)

        assertThat(response).isNotNull
        assertThat(response.nimi).isEqualTo("hankkeen nimi")
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(username)) }
    }

    @Test
    fun `test that the updateHanke will throw if mismatch in hanke tunnus payload vs path`() {
        val hankeUpdate = HankeFactory.create()
        val existingHanke = HankeFactory.create(hankeTunnus = "wrong")
        every { permissionService.hasPermission(existingHanke.id, username, PermissionCode.EDIT) }
            .returns(true)
        every { hankeAuthorizer.authorizeHankeTunnus("wrong", PermissionCode.EDIT) } returns true

        assertThatExceptionOfType(HankeArgumentException::class.java)
            .isThrownBy { hankeController.updateHanke(hankeUpdate, "wrong") }
            .withMessageContaining(
                "Hanketunnus mismatch. (In payload=${hankeUpdate.hankeTunnus}, In path=wrong)"
            )
    }

    @Test
    fun `test that the updateHanke will give validation errors from invalid hanke data for name`() {
        val partialHanke =
            Hanke(
                id = 0,
                hankeTunnus = "id123",
                nimi = "",
                kuvaus = "",
                onYKTHanke = false,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "",
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT
            )

        every { hankeService.loadHanke("id123") }.returns(HankeFactory.create())
        every { hankeService.updateHanke(partialHanke) }.returns(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.nimi: " + HankeError.HAI1002.toString())

        verify { disclosureLogService wasNot Called }
    }

    // sending of sub types
    @Test
    fun `test that create with listOfOmistaja can be sent to controller and is responded with 200`() {
        val request =
            CreateHankeRequest(
                nimi = "hankkeen nimi",
                kuvaus = HankeFactory.defaultKuvaus,
                onYKTHanke = false,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                omistajat =
                    arrayListOf(
                        NewYhteystieto(
                            nimi = "Pekka Pekkanen",
                            email = "pekka@pekka.fi",
                            puhelinnumero = "3212312",
                            organisaatioNimi = "Kaivuri ja mies",
                            osasto = null,
                            rooli = null,
                            tyyppi = YKSITYISHENKILO,
                            alikontaktit =
                                listOf(
                                    Yhteyshenkilo(
                                        "Ali",
                                        "Kontakti",
                                        "ali.kontakti@meili.com",
                                        "050-3789354"
                                    )
                                ),
                        )
                    )
            )
        val mockedHanke = HankeFactory.create(id = 12, hankeTunnus = "JOKU12", nimi = request.nimi)
        mockedHanke.omistajat =
            mutableListOf(request.omistajat!![0].toHankeYhteystieto().copy(id = 1))
        every { hankeService.createHanke(request) }.returns(mockedHanke)

        val response: Hanke = hankeController.createHanke(request)

        assertThat(response).isNotNull
        assertThat(response.id).isEqualTo(12)
        assertThat(response.hankeTunnus).isEqualTo("JOKU12")
        assertThat(response.nimi).isEqualTo("hankkeen nimi")
        assertThat(response.omistajat).isNotNull
        assertThat(response.omistajat).hasSize(1)
        assertThat(response.omistajat[0].id).isEqualTo(1)
        assertThat(response.omistajat[0].nimi).isEqualTo("Pekka Pekkanen")
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(username)) }
    }

    @Test
    fun `test that the updateHanke will give validation errors from null enum values`() {
        val partialHanke =
            Hanke(
                id = 0,
                hankeTunnus = "id123",
                nimi = "",
                kuvaus = "",
                onYKTHanke = false,
                vaihe = null,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "",
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                status = null,
            )
        // mock HankeService response
        every { hankeService.updateHanke(partialHanke) }.returns(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.vaihe: " + HankeError.HAI1002.toString())

        verify { disclosureLogService wasNot Called }
    }
}
