package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.asJsonResource
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class HankeGeometriatServiceImplTest {

    private val hankeRepository: HankeRepository = mockk()

    private val hankeGeometriatDao: HankeGeometriatDao = mockk()

    private val service = HankeGeometriatServiceImpl(hankeRepository, hankeGeometriatDao)

    @Test
    fun `save HankeGeometriat OK - with old version`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val oldHankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        oldHankeGeometriat.hankeId = hankeId
        oldHankeGeometriat.version = 0

        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns HankeEntity(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.updateHankeGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(hankeTunnus, hankeGeometriat)

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
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
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null

        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns HankeEntity(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns null
        every { hankeGeometriatDao.createHankeGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(hankeTunnus, hankeGeometriat)

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.createHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(0)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat - no Hanke`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null

        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns null

        try {
            service.saveGeometriat(hankeTunnus, hankeGeometriat)
            fail("Should have thrown HankeNotFoundException")
        } catch (e: HankeNotFoundException) {
        }

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify(exactly = 0) { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
        verify(exactly = 0) { hankeGeometriatDao.createHankeGeometriat(any()) }
    }

    @Test
    fun `load HankeGeometriat OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns HankeEntity(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns hankeGeometriat

        val loadedHankeGeometriat = service.loadGeometriat(hankeTunnus)

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
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

    @Test
    fun `load HankeGeometriat - no Hanke`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns null

        try {
            service.loadGeometriat(hankeTunnus)
            fail("Should have thrown HankeNotFoundException")
        } catch (e: HankeNotFoundException) {
        }

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify(exactly = 0) { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) }
    }
}