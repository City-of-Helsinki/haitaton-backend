package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.FeatureService
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.geometriat
import fi.hel.haitaton.hanke.factory.HankeBuilder.Companion.toModifyRequest
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.validation.ConstraintViolationException
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

@ExtendWith(SpringExtension::class)
@Import(HankeControllerTest.TestConfiguration::class)
@WithMockUser(USERNAME)
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
        ): HankeController =
            HankeController(
                hankeService,
                permissionService,
            )
    }

    private val mockedHankeTunnus = "AFC1234"

    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var hankeController: HankeController
    @Autowired private lateinit var hankeAuthorizer: HankeAuthorizer

    @BeforeEach
    fun cleanUp() {
        clearAllMocks()
    }

    @Test
    fun `test that the getHankeByTunnus returns ok`() {
        val hankeId = 1234

        every { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
            .returns(true)
        every { hankeService.loadHanke(mockedHankeTunnus) }
            .returns(
                Hanke(
                    hankeId,
                    mockedHankeTunnus,
                    true,
                    "Mannerheimintien remontti remonttinen",
                    HankeFactory.defaultKuvaus,
                    Hankevaihe.OHJELMOINTI,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    HankeStatus.DRAFT,
                ))
        every {
            hankeAuthorizer.authorizeHankeTunnus(mockedHankeTunnus, PermissionCode.VIEW)
        } returns true

        val response = hankeController.getHankeByTunnus(mockedHankeTunnus)

        assertThat(response).isNotNull()
        assertThat(response.nimi).isNotEmpty()
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
                    Hankevaihe.OHJELMOINTI,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    HankeStatus.DRAFT,
                ),
                Hanke(
                    50,
                    "HAME50",
                    true,
                    "Hämeenlinnanväylän uudistus",
                    HankeFactory.defaultKuvaus,
                    Hankevaihe.SUUNNITTELU,
                    1,
                    "Paavo",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    HankeStatus.DRAFT,
                ),
            )
        every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
            .returns(listOf(1234, 50))
        every { hankeService.loadHankkeetByIds(listOf(1234, 50)) }.returns(listOfHanke)

        val hankeList = hankeController.getHankeList(false)

        assertThat(hankeList[0].nimi).isEqualTo("Mannerheimintien remontti remonttinen")
        assertThat(hankeList[1].nimi).isEqualTo("Hämeenlinnanväylän uudistus")
        assertThat(hankeList[0].alueet.geometriat()).isEmpty()
        assertThat(hankeList[1].alueet.geometriat()).isEmpty()
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
                vaihe = Hankevaihe.SUUNNITTELU,
                version = 1,
                createdBy = "Tiina",
                createdAt = getCurrentTimeUTC(),
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT,
            )
        val request = partialHanke.toModifyRequest()
        every { hankeService.updateHanke(hanketunnus, request) }
            .returns(partialHanke.copy(modifiedBy = USERNAME, modifiedAt = getCurrentTimeUTC()))
        every { permissionService.hasPermission(123, USERNAME, PermissionCode.EDIT) }.returns(true)

        val response: Hanke = hankeController.updateHanke(request, hanketunnus)

        assertThat(response).isNotNull()
        assertThat(response.nimi).isEqualTo("hankkeen nimi")
    }

    @Test
    fun `test that the updateHanke will give validation errors from invalid hanke data for name`() {
        val hanketunnus = "id123"
        val partialHanke =
            Hanke(
                id = 0,
                hankeTunnus = hanketunnus,
                nimi = "",
                kuvaus = "",
                onYKTHanke = false,
                vaihe = Hankevaihe.OHJELMOINTI,
                version = 1,
                createdBy = "",
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                status = HankeStatus.DRAFT,
            )
        val request = partialHanke.toModifyRequest()
        every { hankeService.loadHanke(hanketunnus) }.returns(HankeFactory.create())
        every { hankeService.updateHanke(hanketunnus, request) }.returns(partialHanke)

        val failure = assertFailure { hankeController.updateHanke(request, "id123") }

        failure.all {
            hasClass(ConstraintViolationException::class)
            messageContains("updateHanke.hankeUpdate.nimi: " + HankeError.HAI1002.toString())
        }
    }
}
