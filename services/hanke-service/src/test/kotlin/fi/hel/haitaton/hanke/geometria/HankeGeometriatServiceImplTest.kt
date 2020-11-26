package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Paths

internal class HankeGeometriatServiceImplTest {

    private val hankeRepository: HankeRepository = mockk()

    private val hankeGeometriatDao: HankeGeometriatDao = mockk()

    private val service = HankeGeometriatServiceImpl(hankeRepository, hankeGeometriatDao)

    @Test
    fun `save HankeGeometriat OK - with old version`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/test/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        val oldHankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/test/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        oldHankeGeometriat.hankeId = hankeId
        oldHankeGeometriat.version = 1

        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns HankeEntity(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeGeometriatDao.loadHankeGeometriat(hankeId) } returns oldHankeGeometriat
        every { hankeGeometriatDao.deleteHankeGeometriat(any()) } just runs
        every { hankeGeometriatDao.saveHankeGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(hankeTunnus, hankeGeometriat)

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify { hankeGeometriatDao.loadHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        verify { hankeGeometriatDao.saveHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(2)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.updatedAt).isNotNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat OK - without old version`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/test/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null

        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns HankeEntity(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeGeometriatDao.loadHankeGeometriat(hankeId) } returns null
        every { hankeGeometriatDao.deleteHankeGeometriat(any()) } just runs
        every { hankeGeometriatDao.saveHankeGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(hankeTunnus, hankeGeometriat)

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify { hankeGeometriatDao.loadHankeGeometriat(hankeId) }
        verify { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        verify { hankeGeometriatDao.saveHankeGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.updatedAt).isNotNull()
            assertThat(savedHankeGeometria.hankeId).isEqualTo(hankeId)
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save HankeGeometriat - no Hanke`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/test/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null

        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns null

        try {
            service.saveGeometriat(hankeTunnus, hankeGeometriat)
            fail("Should have thrown HankeNotFoundException")
        } catch (e: HankeNotFoundException) {
        }

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify(exactly = 0) { hankeGeometriatDao.loadHankeGeometriat(hankeId) }
        verify(exactly = 0) { hankeGeometriatDao.deleteHankeGeometriat(any()) }
        verify(exactly = 0) { hankeGeometriatDao.saveHankeGeometriat(any()) }
    }

    @Test
    fun `load HankeGeometriat OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/test/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns HankeEntity(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeGeometriatDao.loadHankeGeometriat(hankeId) } returns hankeGeometriat

        val loadedHankeGeometriat = service.loadGeometriat(hankeTunnus)

        verify { hankeRepository.findByHankeTunnus(hankeTunnus) }
        verify { hankeGeometriatDao.loadHankeGeometriat(hankeId) }
        assertAll {
            assertThat(loadedHankeGeometriat).isNotNull()
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(1)
            assertThat(loadedHankeGeometriat.createdAt).isNotNull()
            assertThat(loadedHankeGeometriat.updatedAt).isNotNull()
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
        verify(exactly = 0) { hankeGeometriatDao.loadHankeGeometriat(hankeId) }
    }
}