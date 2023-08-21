package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Perustaja
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import jakarta.validation.ConstraintViolationException
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor

private const val USERNAME = "testuser"
private const val HANKE_NAME_MANNERHEIMINTIE = "Mannerheimintien remontti remonttinen"
private const val HANKE_NAME_HAMEENLINNANVAYLA = "Hämeenlinnanväylän uudistus"
private const val HANKE_MOCK_KUVAUS = "Lorem ipsum dolor sit amet..."

@ExtendWith(SpringExtension::class)
@Import(HankeControllerTest.TestConfiguration::class)
@WithMockUser(USERNAME)
class HankeControllerTest {

    @Configuration
    class TestConfiguration {
        // makes validation happen here in unit test as well
        @Bean fun bean(): MethodValidationPostProcessor = MethodValidationPostProcessor()

        @Bean fun hankeService(): HankeService = Mockito.mock(HankeService::class.java)

        @Bean
        fun permissionService(): PermissionService = Mockito.mock(PermissionService::class.java)

        @Bean fun yhteystietoLoggingService(): DisclosureLogService = mockk(relaxUnitFun = true)

        val featureFlags = FeatureFlags(mapOf(Pair(Feature.HANKE_EDITING, true)))

        @Bean
        fun hankeController(
            hankeService: HankeService,
            permissionService: PermissionService,
            disclosureLogService: DisclosureLogService,
        ): HankeController =
            HankeController(hankeService, permissionService, disclosureLogService, featureFlags)
    }

    private val mockedHankeTunnus = "AFC1234"

    @Autowired private lateinit var hankeService: HankeService

    @Autowired private lateinit var permissionService: PermissionService

    @Autowired private lateinit var hankeController: HankeController

    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    @AfterEach
    fun cleanUp() {
        clearAllMocks()
    }

    @Test
    fun `test that the getHankeByTunnus returns ok`() {
        val hankeId = 1234

        Mockito.`when`(permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW))
            .thenReturn(true)
        Mockito.`when`(hankeService.loadHanke(mockedHankeTunnus))
            .thenReturn(
                Hanke(
                    id = hankeId,
                    hankeTunnus = mockedHankeTunnus,
                    onYKTHanke = true,
                    nimi = HANKE_NAME_MANNERHEIMINTIE,
                    kuvaus = HANKE_MOCK_KUVAUS,
                    vaihe = Vaihe.OHJELMOINTI,
                    suunnitteluVaihe = null,
                    version = 1,
                    createdBy = "Risto",
                    createdAt = getCurrentTimeUTC(),
                    modifiedBy = null,
                    modifiedAt = null,
                    status = HankeStatus.DRAFT,
                    perustaja = HankeFactory.defaultPerustaja,
                )
            )

        val response = hankeController.getHankeByTunnus(mockedHankeTunnus)

        assertThat(response).isNotNull
        assertThat(response.nimi).isNotEmpty
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(USERNAME)) }
    }

    @ParameterizedTest
    @MethodSource("invalidPerustajaArguments")
    fun `test that the createHanke will give validation errors from invalid hanke data for perustaja`(
        perustaja: Perustaja?,
        errorMessage: String,
    ) {
        val partialHanke =
            Hanke(
                id = 0,
                hankeTunnus = null,
                nimi = HANKE_NAME_MANNERHEIMINTIE,
                kuvaus = HANKE_MOCK_KUVAUS,
                onYKTHanke = false,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "",
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT,
                perustaja = perustaja,
            )

        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.createHanke(partialHanke) }
            .withMessageContaining(errorMessage)

        verify { disclosureLogService wasNot Called }
    }

    companion object {
        @JvmStatic
        private fun invalidPerustajaArguments(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    Perustaja(nimi = "Test Person", email = ""),
                    expectedErrorMessage(field = "perustaja.email")
                ),
                Arguments.of(
                    Perustaja(nimi = "", email = "test.mail@mail.com"),
                    expectedErrorMessage(field = "perustaja.nimi")
                ),
                Arguments.of(
                    Perustaja(nimi = null, email = "test.mail@mail.com"),
                    expectedErrorMessage(field = "perustaja.nimi")
                ),
                Arguments.of(null, expectedErrorMessage(field = "perustaja"))
            )

        private fun expectedErrorMessage(field: String) =
            "createHanke.hanke.$field: ${HankeError.HAI1002}"
    }

    @Test
    fun `test when called without parameters then getHankeList returns ok and two items without geometry`() {
        val listOfHanke =
            listOf(
                Hanke(
                    id = 1234,
                    hankeTunnus = mockedHankeTunnus,
                    onYKTHanke = true,
                    nimi = HANKE_NAME_MANNERHEIMINTIE,
                    kuvaus = HANKE_MOCK_KUVAUS,
                    vaihe = Vaihe.OHJELMOINTI,
                    suunnitteluVaihe = null,
                    version = 1,
                    createdBy = "Risto",
                    createdAt = getCurrentTimeUTC(),
                    modifiedBy = null,
                    modifiedAt = null,
                    status = HankeStatus.DRAFT,
                    perustaja = HankeFactory.defaultPerustaja,
                ),
                Hanke(
                    id = 50,
                    hankeTunnus = "HAME50",
                    onYKTHanke = true,
                    nimi = HANKE_NAME_HAMEENLINNANVAYLA,
                    kuvaus = HANKE_MOCK_KUVAUS,
                    vaihe = Vaihe.SUUNNITTELU,
                    suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                    version = 1,
                    createdBy = "Paavo",
                    createdAt = getCurrentTimeUTC(),
                    modifiedBy = null,
                    modifiedAt = null,
                    status = HankeStatus.DRAFT,
                    perustaja = HankeFactory.defaultPerustaja,
                )
            )
        Mockito.`when`(permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW))
            .thenReturn(listOf(1234, 50))
        Mockito.`when`(hankeService.loadHankkeetByIds(listOf(1234, 50))).thenReturn(listOfHanke)

        val hankeList = hankeController.getHankeList(false)

        assertThat(hankeList[0].nimi).isEqualTo(HANKE_NAME_MANNERHEIMINTIE)
        assertThat(hankeList[1].nimi).isEqualTo(HANKE_NAME_HAMEENLINNANVAYLA)
        assertThat(hankeList[0].alueidenGeometriat()).isEmpty()
        assertThat(hankeList[1].alueidenGeometriat()).isEmpty()
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(any(), eq(USERNAME)) }
    }

    @Test
    fun `test that the updateHanke can be called with hanke data and response will be 200`() {
        val partialHanke =
            Hanke(
                id = 123,
                hankeTunnus = "id123",
                nimi = HANKE_NAME_MANNERHEIMINTIE,
                kuvaus = HANKE_MOCK_KUVAUS,
                onYKTHanke = false,
                vaihe = Vaihe.SUUNNITTELU,
                suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT,
                perustaja = HankeFactory.defaultPerustaja,
            )

        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke))
            .thenReturn(partialHanke.copy(modifiedBy = USERNAME, modifiedAt = getCurrentTimeUTC()))
        Mockito.`when`(hankeService.loadHanke("id123"))
            .thenReturn(HankeFactory.create(hankeTunnus = "id123"))
        Mockito.`when`(permissionService.hasPermission(123, USERNAME, PermissionCode.EDIT))
            .thenReturn(true)

        // Actual call
        val response: Hanke = hankeController.updateHanke(partialHanke, "id123")

        assertThat(response).isNotNull
        assertThat(response.nimi).isEqualTo(HANKE_NAME_MANNERHEIMINTIE)
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(USERNAME)) }
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
                status = HankeStatus.DRAFT,
                perustaja = HankeFactory.defaultPerustaja,
            )

        Mockito.`when`(hankeService.loadHanke("id123")).thenReturn(HankeFactory.create())
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.nimi: " + HankeError.HAI1002.toString())

        verify { disclosureLogService wasNot Called }
    }

    // sending of subtypes
    @Test
    fun `test that create with listOfOmistaja can be sent to controller and is responded with 200`() {
        val hanke =
            Hanke(
                id = null,
                hankeTunnus = null,
                nimi = "hankkeen nimi",
                kuvaus = HANKE_MOCK_KUVAUS,
                onYKTHanke = false,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT,
                perustaja = HankeFactory.defaultPerustaja,
            )

        hanke.omistajat =
            arrayListOf(
                HankeYhteystieto(
                    id = null,
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
                                etunimi = "Ali",
                                sukunimi = "Kontakti",
                                email = "ali.kontakti@meili.com",
                                puhelinnumero = "050-3789354"
                            )
                        ),
                )
            )

        val mockedHanke = hanke.copy()
        mockedHanke.omistajat = mutableListOf(hanke.omistajat[0])
        mockedHanke.id = 12
        mockedHanke.hankeTunnus = "JOKU12"
        mockedHanke.omistajat[0].id = 1

        // mock HankeService response
        Mockito.`when`(hankeService.createHanke(hanke)).thenReturn(mockedHanke)

        // Actual call
        val response: Hanke = hankeController.createHanke(hanke)

        assertThat(response).isNotNull
        assertThat(response.id).isEqualTo(12)
        assertThat(response.hankeTunnus).isEqualTo("JOKU12")
        assertThat(response.nimi).isEqualTo("hankkeen nimi")
        assertThat(response.omistajat).isNotNull
        assertThat(response.omistajat).hasSize(1)
        assertThat(response.omistajat[0].id).isEqualTo(1)
        assertThat(response.omistajat[0].nimi).isEqualTo("Pekka Pekkanen")
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(USERNAME)) }
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
                perustaja = HankeFactory.defaultPerustaja
            )
        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.vaihe: " + HankeError.HAI1002.toString())

        verify { disclosureLogService wasNot Called }
    }
}
