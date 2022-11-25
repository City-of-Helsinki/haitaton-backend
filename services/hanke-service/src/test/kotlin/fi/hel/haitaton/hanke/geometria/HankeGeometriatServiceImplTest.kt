package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

internal class HankeGeometriatServiceImplTest {

    private val hankeService: HankeService = mockk()

    private val hankeGeometriatDao: HankeGeometriatDao = mockk()

    private val service = HankeGeometriatServiceImpl(hankeGeometriatDao)

    companion object {

        @BeforeAll
        @JvmStatic
        fun setUp() {
            val securityContext: SecurityContext = mockk()
            val authentication: Authentication = mockk()
            every { securityContext.authentication } returns authentication
            every { authentication.name } returns "tester"
            SecurityContextHolder.setContext(securityContext)
        }
    }

    private fun loadGeometriat(): HankeGeometriat {
        return "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
            HankeGeometriat::class.java
        )
    }

    @Test
    fun `save HankeGeometriat OK - with old version`() {
        val gid = 42
        val hankeGeometriat = loadGeometriat()
        hankeGeometriat.id = gid
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val oldHankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                HankeGeometriat::class.java
            )
        oldHankeGeometriat.id = gid
        oldHankeGeometriat.version = 0

        every { hankeGeometriatDao.retrieveGeometriat(gid) } returns oldHankeGeometriat
        every { hankeGeometriatDao.updateHankeGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(hankeGeometriat)

        verify { hankeGeometriatDao.retrieveGeometriat(gid) }
        verify { hankeGeometriatDao.updateHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat OK - without old version`() {
        val hankeGeometriat = loadGeometriat()
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null

        every { hankeGeometriatDao.createHankeGeometriat(any()) } returns hankeGeometriat

        val savedHankeGeometria = service.saveGeometriat(hankeGeometriat)

        verify(inverse = true) { hankeGeometriatDao.retrieveGeometriat(any()) }
        verify { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(0)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat OK - without features (delete)`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = loadGeometriat()
        val oldHankeGeometriat = loadGeometriat()
        oldHankeGeometriat.version = 0

        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.deleteHankeGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(hankeGeometriat)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveGeometriat(hankeId) }
        verify { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
            assertThat(savedHankeGeometria.featureCollection!!.features).isEmpty()
        }
    }

    @Test
    fun `save HankeGeometriat OK - with an empty old version (after delete)`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = loadGeometriat()
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val oldHankeGeometriat = loadGeometriat()
        oldHankeGeometriat.version = 1
        oldHankeGeometriat.featureCollection!!.features.clear()

        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.updateHankeGeometriat(any()) } just runs

        val hanke = hankeService.loadHanke(hankeTunnus)
        val savedHankeGeometria = service.saveGeometriat(hankeGeometriat)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveGeometriat(hankeId) }
        verify { hankeGeometriatDao.updateHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(2)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
            assertThat(savedHankeGeometria.featureCollection!!.features).isNotEmpty()
        }
    }

    @Test
    fun `load HankeGeometriat OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = loadGeometriat()
        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveGeometriat(hankeId) } returns hankeGeometriat

        val hanke = hankeService.loadHanke(hankeTunnus)
        val loadedHankeGeometriat = service.loadGeometriat(hanke!!)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveGeometriat(hankeId) }
        assertAll {
            assertThat(loadedHankeGeometriat).isNotNull()
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(1)
            assertThat(loadedHankeGeometriat.createdAt).isNotNull()
            assertThat(loadedHankeGeometriat.modifiedAt).isNotNull()
            assertThat(loadedHankeGeometriat.featureCollection).isNotNull()
        }
    }
}
