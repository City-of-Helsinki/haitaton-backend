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
import fi.hel.haitaton.hanke.factory.HankeFactory
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

internal class GeometriatServiceImplTest {

    private val hankeService: HankeService = mockk()

    private val geometriatDao: GeometriatDao = mockk()

    private val service = GeometriatServiceImpl(geometriatDao)

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

    private fun loadGeometriat(): Geometriat {
        return "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()
    }

    @Test
    fun `save Geometriat OK - with old version`() {
        val geometriaId = 42
        val geometriat = loadGeometriat()
        geometriat.id = geometriaId
        geometriat.version = null
        geometriat.createdAt = null
        geometriat.modifiedAt = null
        val oldGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )
        oldGeometriat.id = geometriaId
        oldGeometriat.version = 0

        every { geometriatDao.retrieveGeometriat(geometriaId) } returns oldGeometriat
        every { geometriatDao.updateGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(geometriat)

        verify { geometriatDao.retrieveGeometriat(geometriaId) }
        verify { geometriatDao.updateGeometriat(any()) }
        verify(exactly = 0) { geometriatDao.createGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save Geometriat OK - without old version`() {
        val geometriat = loadGeometriat()
        geometriat.version = null
        geometriat.createdAt = null
        geometriat.modifiedAt = null

        every { geometriatDao.createGeometriat(any()) } returns geometriat

        val savedHankeGeometria = service.saveGeometriat(geometriat)

        verify(inverse = true) { geometriatDao.retrieveGeometriat(any()) }
        verify { geometriatDao.createGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(0)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
        }
    }

    @Test
    fun `save Geometriat OK - without features (delete)`() {
        val geometriatId = 1
        val geometriat: Geometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat-delete.json".asJsonResource()
        geometriat.id = geometriatId
        val oldGeometriat = loadGeometriat()
        oldGeometriat.version = 0
        oldGeometriat.id = geometriatId

        every { geometriatDao.retrieveGeometriat(geometriatId) } returns oldGeometriat
        every { geometriatDao.deleteGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(geometriat)

        verify { geometriatDao.retrieveGeometriat(geometriatId) }
        verify { geometriatDao.deleteGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(1)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
            assertThat(savedHankeGeometria.featureCollection!!.features).isEmpty()
        }
    }

    @Test
    fun `save Geometriat OK - with an empty old version (after delete)`() {
        val geometriatId = 1
        val geometriat = loadGeometriat()
        geometriat.id = geometriatId
        geometriat.version = null
        geometriat.createdAt = null
        geometriat.modifiedAt = null
        val oldGeometriat = loadGeometriat()
        oldGeometriat.id = geometriatId
        oldGeometriat.version = 1
        oldGeometriat.featureCollection!!.features.clear()

        every { geometriatDao.retrieveGeometriat(geometriatId) } returns oldGeometriat
        every { geometriatDao.updateGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(geometriat)

        verify { geometriatDao.retrieveGeometriat(geometriatId) }
        verify { geometriatDao.updateGeometriat(any()) }
        verify(exactly = 0) { geometriatDao.deleteGeometriat(any()) }
        verify(exactly = 0) { geometriatDao.createGeometriat(any()) }
        assertAll {
            assertThat(savedHankeGeometria.version).isEqualTo(2)
            assertThat(savedHankeGeometria.createdAt).isNotNull()
            assertThat(savedHankeGeometria.modifiedAt).isNotNull()
            assertThat(savedHankeGeometria.featureCollection).isNotNull()
            assertThat(savedHankeGeometria.featureCollection!!.features).isNotEmpty()
        }
    }

    @Test
    fun `load Geometriat OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val geometriat = loadGeometriat()
        val hanke = HankeFactory.create(id = hankeId, hankeTunnus = hankeTunnus)
        every { geometriatDao.retrieveGeometriat(hankeId) } returns geometriat

        val loadedGeometriat = service.loadGeometriat(hanke)

        verify { geometriatDao.retrieveGeometriat(hankeId) }
        assertAll {
            assertThat(loadedGeometriat).isNotNull()
            assertThat(loadedGeometriat!!.version).isEqualTo(1)
            assertThat(loadedGeometriat.createdAt).isNotNull()
            assertThat(loadedGeometriat.modifiedAt).isNotNull()
            assertThat(loadedGeometriat.featureCollection).isNotNull()
        }
    }
}
