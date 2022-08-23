package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.HankeNotFoundException
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
import org.junit.jupiter.api.fail
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

    @Test
    fun `save HankeGeometriat OK - with old version`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val oldHankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        oldHankeGeometriat.hankeId = hankeId
        oldHankeGeometriat.version = 0

        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.updateHankeGeometriat(any()) } just runs

        val hanke = hankeService.loadHanke(hankeTunnus)
        val savedHankeGeometria = service.saveGeometriat(hanke!!, hankeGeometriat)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.updateHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat OK - without old version`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null

        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns null
        every { hankeGeometriatDao.createHankeGeometriat(any()) } just runs

        val hanke = hankeService.loadHanke(hankeTunnus)
        val savedHankeGeometria = service.saveGeometriat(hanke!!, hankeGeometriat)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(0)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat OK - without features (delete)`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat-delete.json"
            .asJsonResource(HankeGeometriat::class.java)
        val oldHankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        oldHankeGeometriat.hankeId = hankeId
        oldHankeGeometriat.version = 0

        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.deleteHankeGeometriat(any()) } just runs

        val hanke = hankeService.loadHanke(hankeTunnus)
        val savedHankeGeometria = service.saveGeometriat(hanke!!, hankeGeometriat)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
            assertThat(savedHankeGeometria.featureCollection!!.features).isEmpty()
        }
    }

    @Test
    fun `save HankeGeometriat OK - with an empty old version (after delete)`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val oldHankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        oldHankeGeometriat.hankeId = hankeId
        oldHankeGeometriat.version = 1
        oldHankeGeometriat.featureCollection!!.features.clear()

        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.updateHankeGeometriat(any()) } just runs

        val hanke = hankeService.loadHanke(hankeTunnus)
        val savedHankeGeometria = service.saveGeometriat(hanke!!, hankeGeometriat)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.updateHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(2)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
            assertThat(savedHankeGeometria.featureCollection!!.features).isNotEmpty()
        }
    }

    @Test
    fun `load HankeGeometriat OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        every { hankeService.loadHanke(hankeTunnus) } returns Hanke(hankeId, hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns hankeGeometriat

        val hanke = hankeService.loadHanke(hankeTunnus)
        val loadedHankeGeometriat = service.loadGeometriat(hanke!!)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        assertAll {
            assertThat(loadedHankeGeometriat).isNotNull()
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(1)
            assertThat(loadedHankeGeometriat.createdAt).isNotNull()
            assertThat(loadedHankeGeometriat.modifiedAt).isNotNull()
            assertThat(loadedHankeGeometriat.hankeId).isEqualTo(hankeId)
            assertThat(loadedHankeGeometriat.featureCollection).isNotNull()
        }
    }

}
