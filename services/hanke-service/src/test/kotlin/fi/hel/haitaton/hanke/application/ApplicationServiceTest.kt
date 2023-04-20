package fi.hel.haitaton.hanke.application

import assertk.all
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.AlluException
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.Called
import io.mockk.called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.test.context.junit.jupiter.SpringExtension

private const val username = "test"
private const val hankeTunnus = "HAI-1234"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ApplicationServiceTest {
    private val applicationRepo: ApplicationRepository = mockk()
    private val statusRepo: AlluStatusRepository = mockk()
    private val cableReportService: CableReportService = mockk()
    private val geometriatDao: GeometriatDao = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val applicationLoggingService: ApplicationLoggingService = mockk(relaxUnitFun = true)
    private val hankeRepository: HankeRepository = mockk()
    private val permissionService: PermissionService = mockk()
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)

    private val service: ApplicationService =
        ApplicationService(
            applicationRepo,
            statusRepo,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
            hankeKayttajaService,
            geometriatDao,
            permissionService,
            hankeRepository,
        )

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun verifyMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            applicationRepo,
            statusRepo,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
            hankeKayttajaService,
            geometriatDao,
        )
    }

    private val applicationData: CableReportApplicationData =
        "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()

    @Test
    fun create() {
        val hankeTunnus = "HAI-1234"
        val dto =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = applicationData,
                hankeTunnus = hankeTunnus,
            )
        every { applicationRepo.save(any()) } answers
            {
                val application: ApplicationEntity = firstArg()
                application.copy(id = 1)
            }
        val hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus)
        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns hanke
        every { geometriatDao.validateGeometriat(any()) } returns null
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        val created = service.create(dto, username)

        assertThat(created.id).isEqualTo(1)
        assertThat(created.alluid).isEqualTo(null)
        verify {
            applicationRepo.save(any())
            applicationLoggingService.logCreate(any(), username)
            geometriatDao.validateGeometriat(any())
            geometriatDao.isInsideHankeAlueet(1, any())
            disclosureLogService wasNot Called
            cableReportService wasNot Called
        }
    }

    @Test
    fun `create throws exception with invalid geometry`() {
        val dto =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = applicationData,
                hankeTunnus = hankeTunnus
            )
        every { geometriatDao.validateGeometriat(any()) } returns
            GeometriatDao.InvalidDetail(
                "Self-intersection",
                """{"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )

        val exception = assertThrows<ApplicationGeometryException> { service.create(dto, username) }

        assertThat(exception)
            .hasMessage(
                """Invalid geometry received when creating a new application for user $username, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )
        verify { geometriatDao.validateGeometriat(any()) }
    }

    @Test
    fun `updateApplicationData saves disclosure logs when updating Allu data`() {
        val hankeTunnus = "HAI-1234"
        val hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = 42,
                userId = username,
                applicationData = applicationData,
                hanke = hanke,
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { applicationRepo.save(applicationEntity) } returns applicationEntity
        justRun { cableReportService.update(42, any()) }
        justRun { cableReportService.addAttachment(42, any()) }
        every { cableReportService.getApplicationInformation(42) } returns
            AlluDataFactory.createAlluApplicationResponse(42)
        every { geometriatDao.validateGeometriat(any()) } returns null
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        val updatedData = applicationData.copy(rockExcavation = !applicationData.rockExcavation!!)

        service.updateApplicationData(3, updatedData, username)

        verifyOrder {
            applicationRepo.findOneById(3)
            geometriatDao.validateGeometriat(any())
            geometriatDao.isInsideHankeAlueet(1, any())
            cableReportService.getApplicationInformation(42)
            // any() here tries to match eq([]) for some reason
            geometriatDao.calculateCombinedArea(listOf(applicationData.areas?.first()?.geometry!!))
            geometriatDao.calculateArea(any())
            cableReportService.update(42, any())
            disclosureLogService.saveDisclosureLogsForAllu(updatedData, Status.SUCCESS)
            cableReportService.addAttachment(42, any())
            applicationRepo.save(applicationEntity)
            applicationLoggingService.logUpdate(any(), any(), username)
        }
    }

    @Test
    fun `updateApplicationData throws exception with invalid geometry`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = 42,
                userId = username,
                applicationData = applicationData,
                hanke = HankeEntity(hankeTunnus = hankeTunnus),
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.validateGeometriat(any()) } returns
            GeometriatDao.InvalidDetail(
                "Self-intersection",
                """{"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )
        val updatedData = applicationData.copy(rockExcavation = !applicationData.rockExcavation!!)

        val exception =
            assertThrows<ApplicationGeometryException> {
                service.updateApplicationData(3, updatedData, username)
            }

        assertThat(exception)
            .hasMessage(
                """Invalid geometry received when updating application for user $username, id=3, alluid=42, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )
        verify {
            applicationRepo.findOneById(3)
            geometriatDao.validateGeometriat(any())
        }
    }

    @Test
    fun `sendApplication saves disclosure logs for successful attempts`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData,
                hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus),
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { applicationRepo.save(any()) } answers { firstArg() }
        every { cableReportService.create(any()) } returns 42
        justRun { cableReportService.addAttachment(42, any()) }
        every { cableReportService.getApplicationInformation(42) } returns
            AlluDataFactory.createAlluApplicationResponse(42)
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        service.sendApplication(3, username)

        val expectedApplication = applicationData.copy(pendingOnClient = false)
        verifyOrder {
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            hankeKayttajaService.saveNewTokensFromApplication(applicationData, 1)
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(any())
            disclosureLogService.saveDisclosureLogsForAllu(expectedApplication, Status.SUCCESS)
            cableReportService.addAttachment(42, any())
            cableReportService.getApplicationInformation(42)
            applicationRepo.save(any())
        }
    }

    @Test
    fun `sendApplication saves disclosure logs for failed attempts`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData,
                hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus),
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        every { cableReportService.create(any()) } throws AlluException(listOf())
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        assertThrows<AlluException> { service.sendApplication(3, username) }

        val expectedApplication = applicationData.copy(pendingOnClient = false)
        verifyOrder {
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            hankeKayttajaService.saveNewTokensFromApplication(applicationData, 1)
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(any())
            disclosureLogService.saveDisclosureLogsForAllu(
                expectedApplication,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
        }
    }

    @Test
    fun `sendApplication doesn't save disclosure logs for login errors`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData,
                hanke = HankeEntity(hankeTunnus = hankeTunnus, id = 1),
            )
        assertThat(applicationEntity.applicationData.areas).isNotNull().isNotEmpty()
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.calculateCombinedArea(any()) } returns 500f
        every { geometriatDao.calculateArea(any()) } returns 500f
        every { geometriatDao.isInsideHankeAlueet(any(), any()) } returns true
        every { cableReportService.create(any()) } throws AlluLoginException(RuntimeException())

        assertThrows<AlluLoginException> { service.sendApplication(3, username) }

        verifyOrder {
            disclosureLogService wasNot called
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(any(), any())
            hankeKayttajaService.saveNewTokensFromApplication(applicationData, 1)
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(any())
        }
    }

    @ParameterizedTest
    @CsvSource("true,Louhitaan", "false,Ei louhita")
    fun `sendApplication adds rock excavation information to work description`(
        rockExcavation: Boolean,
        expectedSuffix: String
    ) {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData.copy(rockExcavation = rockExcavation),
                hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus),
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { applicationRepo.save(any()) } answers { firstArg() }
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
        every { cableReportService.create(any()) } returns 852
        justRun { cableReportService.addAttachment(852, any()) }
        every { cableReportService.getApplicationInformation(852) } returns
            AlluDataFactory.createAlluApplicationResponse(852)

        service.sendApplication(3, username)

        val expectedApplicationData =
            applicationData.copy(pendingOnClient = false, rockExcavation = rockExcavation)
        val expectedAlluData =
            expectedApplicationData
                .toAlluData()
                .copy(workDescription = applicationData.workDescription + "\n" + expectedSuffix)
        verifyOrder {
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            hankeKayttajaService.saveNewTokensFromApplication(any(), 1)
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(expectedAlluData)
            disclosureLogService.saveDisclosureLogsForAllu(expectedApplicationData, Status.SUCCESS)
            cableReportService.addAttachment(852, any())
            cableReportService.getApplicationInformation(852)
            applicationRepo.save(any())
        }
    }

    @ParameterizedTest(name = "{1} {2}")
    @MethodSource("invalidApplicationData")
    fun `sendApplication with invalid data doesn't send application to Allu`(
        applicationData: ApplicationData,
        path: String,
    ) {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData,
                hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus),
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        assertThat { service.sendApplication(3, username) }
            .isFailure()
            .all {
                this.hasClass(AlluDataException::class)
                this.hasMessage("Application data failed validation at $path: Can't be null")
            }

        verify {
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            hankeKayttajaService.saveNewTokensFromApplication(applicationData, 1)
            cableReportService wasNot Called
            disclosureLogService wasNot Called
            applicationLoggingService wasNot Called
        }
    }

    private fun invalidApplicationData(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                applicationData.copy(
                    customerWithContacts =
                        applicationData.customerWithContacts.copy(
                            customer =
                                applicationData.customerWithContacts.customer.copy(type = null)
                        )
                ),
                "applicationData.customerWithContacts.customer.type",
            ),
            Arguments.of(
                applicationData.copy(endTime = null),
                "applicationData.endTime",
            ),
            Arguments.of(
                applicationData.copy(startTime = null),
                "applicationData.startTime",
            ),
            Arguments.of(
                applicationData.copy(rockExcavation = null),
                "applicationData.rockExcavation",
            ),
        )
    }
}
