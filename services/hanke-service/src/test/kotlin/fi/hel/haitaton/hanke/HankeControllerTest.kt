package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionProfiles
import fi.hel.haitaton.hanke.permissions.PermissionService
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

@ExtendWith(SpringExtension::class)
@Import(HankeControllerTest.TestConfiguration::class)
@WithMockUser
class HankeControllerTest {

    @Configuration
    class TestConfiguration {
        // makes validation happen here in unit test as well
        @Bean fun bean(): MethodValidationPostProcessor = MethodValidationPostProcessor()

        @Bean fun hankeService(): HankeService = Mockito.mock(HankeService::class.java)

        @Bean
        fun hankeGeometriatService(): HankeGeometriatService =
            Mockito.mock(HankeGeometriatService::class.java)

        @Bean
        fun permissionService(): PermissionService = Mockito.mock(PermissionService::class.java)

        @Bean fun yhteystietoLoggingService(): DisclosureLogService = mockk(relaxUnitFun = true)

        @Bean
        fun hankeController(
            hankeService: HankeService,
            hankeGeometriatService: HankeGeometriatService,
            permissionService: PermissionService,
            disclosureLogService: DisclosureLogService,
        ): HankeController =
            HankeController(
                hankeService,
                hankeGeometriatService,
                permissionService,
                disclosureLogService
            )
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
        val permission = Permission(1, "user", hankeId, listOf(PermissionCode.VIEW))

        Mockito.`when`(permissionService.getPermissionByHankeIdAndUserId(hankeId, "user"))
            .thenReturn(permission)
        Mockito.`when`(hankeService.loadHanke(mockedHankeTunnus))
            .thenReturn(
                Hanke(
                    hankeId,
                    mockedHankeTunnus,
                    true,
                    "Mannerheimintien remontti remonttinen",
                    "Lorem ipsum dolor sit amet...",
                    getDatetimeAlku(),
                    getDatetimeLoppu(),
                    Vaihe.OHJELMOINTI,
                    null,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    SaveType.DRAFT
                )
            )

        val response = hankeController.getHankeByTunnus(mockedHankeTunnus)

        assertThat(response).isNotNull
        assertThat(response.nimi).isNotEmpty
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq("user")) }
    }

    @WithMockUser
    @Test
    fun `test when called without parameters then getHankeList returns ok and two items without geometry`() {
        val permissions =
            listOf(
                Permission(1, "user", 1234, listOf(PermissionCode.VIEW)),
                Permission(2, "user", 50, listOf(PermissionCode.VIEW, PermissionCode.EDIT))
            )
        val listOfHanke =
            listOf(
                Hanke(
                    1234,
                    mockedHankeTunnus,
                    true,
                    "Mannerheimintien remontti remonttinen",
                    "Lorem ipsum dolor sit amet...",
                    getDatetimeAlku(),
                    getDatetimeLoppu(),
                    Vaihe.OHJELMOINTI,
                    null,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    SaveType.DRAFT
                ),
                Hanke(
                    50,
                    "HAME50",
                    true,
                    "Hämeenlinnanväylän uudistus",
                    "Lorem ipsum dolor sit amet...",
                    getDatetimeAlku(),
                    getDatetimeLoppu(),
                    Vaihe.SUUNNITTELU,
                    SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                    1,
                    "Paavo",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    SaveType.SUBMIT
                )
            )
        Mockito.`when`(permissionService.getPermissionsByUserId("user")).thenReturn(permissions)
        Mockito.`when`(hankeService.loadHankkeetByIds(listOf(1234, 50))).thenReturn(listOfHanke)

        val hankeList = hankeController.getHankeList(false)

        assertThat(hankeList[0].nimi).isEqualTo("Mannerheimintien remontti remonttinen")
        assertThat(hankeList[1].nimi).isEqualTo("Hämeenlinnanväylän uudistus")
        assertThat(hankeList[0].alueidenGeometriat()).isEmpty()
        assertThat(hankeList[1].alueidenGeometriat()).isEmpty()
        assertThat(hankeList[0].permissions).isEqualTo(listOf(PermissionCode.VIEW))
        assertThat(hankeList[1].permissions)
            .isEqualTo(listOf(PermissionCode.VIEW, PermissionCode.EDIT))
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(any(), eq("user")) }
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
                alkuPvm = getDatetimeAlku(),
                loppuPvm = getDatetimeLoppu(),
                vaihe = Vaihe.SUUNNITTELU,
                suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                saveType = SaveType.DRAFT
            )

        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke))
            .thenReturn(partialHanke.copy(modifiedBy = "user", modifiedAt = getCurrentTimeUTC()))

        // Actual call
        val response: Hanke = hankeController.updateHanke(partialHanke, "id123")

        assertThat(response).isNotNull
        assertThat(response.nimi).isEqualTo("hankkeen nimi")
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq("user")) }
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
                alkuPvm = null,
                loppuPvm = null,
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "",
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                saveType = SaveType.DRAFT
            )
        // mock HankeService response
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
                alkuPvm = getDatetimeAlku(),
                loppuPvm = getDatetimeLoppu(),
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                saveType = SaveType.DRAFT
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
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq("Tiina")) }
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
                alkuPvm = null,
                loppuPvm = null,
                vaihe = null,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "",
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                saveType = null
            )
        // mock HankeService response
        Mockito.`when`(hankeService.updateHanke(partialHanke)).thenReturn(partialHanke)

        // Actual call
        assertThatExceptionOfType(ConstraintViolationException::class.java)
            .isThrownBy { hankeController.updateHanke(partialHanke, "id123") }
            .withMessageContaining("updateHanke.hanke.vaihe: " + HankeError.HAI1002.toString())
            .withMessageContaining("updateHanke.hanke.saveType: " + HankeError.HAI1002.toString())

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
                alkuPvm = getDatetimeAlku(),
                loppuPvm = getDatetimeLoppu(),
                vaihe = Vaihe.OHJELMOINTI,
                suunnitteluVaihe = null,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                saveType = SaveType.DRAFT
            )

        val mockedHanke = hanke.copy()
        mockedHanke.id = 12
        mockedHanke.hankeTunnus = "JOKU12"

        Mockito.`when`(hankeService.createHanke(hanke)).thenReturn(mockedHanke)

        val response: Hanke = hankeController.createHanke(hanke)

        assertThat(response).isNotNull
        Mockito.verify(permissionService)
            .setPermission(12, hanke.createdBy!!, PermissionProfiles.HANKE_OWNER_PERMISSIONS)
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), eq("Tiina")) }
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
