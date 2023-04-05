package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
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

        @Bean fun hankeService(): HankeService = Mockito.mock(HankeService::class.java)

        @Bean
        fun permissionService(): PermissionService = Mockito.mock(PermissionService::class.java)

        @Bean fun yhteystietoLoggingService(): DisclosureLogService = mockk(relaxUnitFun = true)

        @Bean
        fun hankeController(
            hankeService: HankeService,
            permissionService: PermissionService,
            disclosureLogService: DisclosureLogService,
        ): HankeController = HankeController(hankeService, permissionService, disclosureLogService)
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

        Mockito.`when`(permissionService.hasPermission(hankeId, username, PermissionCode.VIEW))
            .thenReturn(true)
        Mockito.`when`(hankeService.loadHanke(mockedHankeTunnus))
            .thenReturn(
                Hanke(
                    hankeId,
                    mockedHankeTunnus,
                    true,
                    "Mannerheimintien remontti remonttinen",
                    "Lorem ipsum dolor sit amet...",
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
                    "Lorem ipsum dolor sit amet...",
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
                    "Lorem ipsum dolor sit amet...",
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
        Mockito.`when`(permissionService.getAllowedHankeIds(username, PermissionCode.VIEW))
            .thenReturn(listOf(1234, 50))
        Mockito.`when`(hankeService.loadHankkeetByIds(listOf(1234, 50))).thenReturn(listOfHanke)

        val hankeList = hankeController.getHankeList(false)

        assertThat(hankeList[0].nimi).isEqualTo("Mannerheimintien remontti remonttinen")
        assertThat(hankeList[1].nimi).isEqualTo("Hämeenlinnanväylän uudistus")
        assertThat(hankeList[0].alueidenGeometriat()).isEmpty()
        assertThat(hankeList[1].alueidenGeometriat()).isEmpty()
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(any(), eq(username)) }
    }

    @Test
    fun `test that the updateHanke can be called with hanke data and response will be 200`() {
        val partialHanke =
            Hanke(
                id = 123,
                hankeTunnus = "id123",
                nimi = "hankkeen nimi",
                kuvaus = "lorem ipsum dolor sit amet...",
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
        Mockito.`when`(hankeService.updateHanke(partialHanke))
            .thenReturn(partialHanke.copy(modifiedBy = username, modifiedAt = getCurrentTimeUTC()))
        Mockito.`when`(hankeService.loadHanke("id123"))
            .thenReturn(HankeFactory.create(hankeTunnus = "id123"))
        Mockito.`when`(permissionService.hasPermission(123, username, PermissionCode.EDIT))
            .thenReturn(true)

        // Actual call
        val response: Hanke = hankeController.updateHanke(partialHanke, "id123")

        assertThat(response).isNotNull
        assertThat(response.nimi).isEqualTo("hankkeen nimi")
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(username)) }
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

        Mockito.`when`(hankeService.loadHanke("id123")).thenReturn(HankeFactory.create())
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.nimi: " + HankeError.HAI1002.toString())

        verify { disclosureLogService wasNot Called }
    }

    // sending of sub types
    @Test
    fun `test that create with listOfOmistaja can be sent to controller and is responded with 200`() {
        val hanke =
            Hanke(
                id = null,
                hankeTunnus = null,
                nimi = "hankkeen nimi",
                kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT,
            )

        hanke.omistajat =
            arrayListOf(
                HankeYhteystieto(
                    null,
                    "Pekkanen",
                    "Pekka",
                    "pekka@pekka.fi",
                    "3212312",
                    null,
                    "Kaivuri ja mies",
                    null,
                    null,
                    null,
                    null
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
        assertThat(response.omistajat[0].sukunimi).isEqualTo("Pekkanen")
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
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.vaihe: " + HankeError.HAI1002.toString())

        verify { disclosureLogService wasNot Called }
    }

    @Test
    fun `test that creating a Hanke also adds owner permissions for creating user`() {
        val hanke =
            Hanke(
                id = null,
                hankeTunnus = null,
                nimi = "hankkeen nimi",
                kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT
            )

        val mockedHanke = hanke.copy()
        mockedHanke.id = 12
        mockedHanke.hankeTunnus = "JOKU12"

        Mockito.`when`(hankeService.createHanke(hanke)).thenReturn(mockedHanke)

        val response: Hanke = hankeController.createHanke(hanke)

        assertThat(response).isNotNull
        Mockito.verify(permissionService).setPermission(12, username, Role.KAIKKI_OIKEUDET)
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq(username)) }
    }

    private fun getDatetimeAlku(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC).truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getDatetimeLoppu(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC).truncatedTo(ChronoUnit.MILLIS)
    }
}
