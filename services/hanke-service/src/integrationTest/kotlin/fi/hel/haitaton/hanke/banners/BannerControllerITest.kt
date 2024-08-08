package fi.hel.haitaton.hanke.banners

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [BannerController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
class BannerControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var bannerService: BannerService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(bannerService)
    }

    @Nested
    inner class ListBanners {
        private val url = "/banners"

        @Test
        fun `returns banners for an anonymous user`() {
            every { bannerService.listBanners() } returns
                BannerFactory.createResponseMap(BannerType.INFO)

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.INFO.label.fi").value("Finnish INFO label"))
                .andExpect(jsonPath("$.INFO.label.sv").value("Swedish INFO label"))
                .andExpect(jsonPath("$.INFO.label.en").value("English INFO label"))
                .andExpect(jsonPath("$.INFO.text.fi").value("Finnish INFO body"))
                .andExpect(jsonPath("$.INFO.text.sv").value("Swedish INFO body"))
                .andExpect(jsonPath("$.INFO.text.en").value("English INFO body"))

            verifySequence { bannerService.listBanners() }
        }

        @Test
        fun `returns empty object when there are no banners`() {
            every { bannerService.listBanners() } returns mapOf()

            get(url).andExpect(status().isOk).andExpect(content().string("{}"))

            verifySequence { bannerService.listBanners() }
        }
    }
}
