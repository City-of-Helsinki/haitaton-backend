package fi.hel.haitaton.hanke

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Paths

internal class HankeGeometriaServiceImplTest {

    private val dao: HankeDao = mockk()

    private val service = HankeGeometriaServiceImpl(dao)

    @Test
    fun `save HankeGeometriat OK - with old version`() {
        val hankeId = "1234567"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        val oldHankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        oldHankeGeometriat.hankeId = hankeId
        oldHankeGeometriat.version = 1

        every { dao.findHankeByHankeId(hankeId) } returns HankeEntity(hankeId)
        every { dao.loadHankeGeometria(HankeEntity(hankeId)) } returns oldHankeGeometriat
        every { dao.saveHankeGeometria(any(), any()) } just runs

        val savedHankeGeometria = service.saveGeometria(hankeId, hankeGeometriat)

        verify { dao.findHankeByHankeId(hankeId) }
        verify { dao.loadHankeGeometria(HankeEntity(hankeId)) }
        verify { dao.saveHankeGeometria(any(), any()) }
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
        val hankeId = "1234567"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null

        every { dao.findHankeByHankeId(hankeId) } returns HankeEntity(hankeId)
        every { dao.loadHankeGeometria(HankeEntity(hankeId)) } returns null
        every { dao.saveHankeGeometria(any(), any()) } just runs

        val savedHankeGeometria = service.saveGeometria(hankeId, hankeGeometriat)

        verify { dao.findHankeByHankeId(hankeId) }
        verify { dao.loadHankeGeometria(HankeEntity(hankeId)) }
        verify { dao.saveHankeGeometria(any(), any()) }
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
        val hankeId = "1234567"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null

        every { dao.findHankeByHankeId(hankeId) } returns null

        try {
            service.saveGeometria(hankeId, hankeGeometriat)
            fail("Should have thrown HankeNotFoundException")
        } catch (e: HankeNotFoundException) {
        }

        verify { dao.findHankeByHankeId(hankeId) }
        verify(exactly = 0) { dao.loadHankeGeometria(HankeEntity(hankeId)) }
        verify(exactly = 0) { dao.saveHankeGeometria(any(), any()) }
    }

    @Test
    fun `load HankeGeometriat OK`() {
        val hankeId = "1234567"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        every { dao.findHankeByHankeId(hankeId) } returns HankeEntity(hankeId)
        every { dao.loadHankeGeometria(HankeEntity(hankeId)) } returns hankeGeometriat

        val loadedHankeGeometriat = service.loadGeometria(hankeId)

        verify { dao.findHankeByHankeId(hankeId) }
        verify { dao.loadHankeGeometria(HankeEntity(hankeId)) }
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
        val hankeId = "1234567"

        every { dao.findHankeByHankeId(hankeId) } returns null

        try {
            service.loadGeometria(hankeId)
            fail("Should have thrown HankeNotFoundException")
        } catch (e: HankeNotFoundException) {
        }

        verify { dao.findHankeByHankeId(hankeId) }
        verify(exactly = 0) { dao.loadHankeGeometria(HankeEntity(hankeId)) }
    }
}