package fi.hel.haitaton.hanke.geometria

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.asJsonResource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

internal class GeometriatServiceImplTest {

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
        geometriat.id = null
        geometriat.version = null
        geometriat.createdAt = null
        geometriat.modifiedAt = null
        val oldGeometriat = loadGeometriat()
        oldGeometriat.id = geometriaId
        oldGeometriat.version = 0

        every { geometriatDao.retrieveGeometriat(geometriaId) } returns oldGeometriat
        every { geometriatDao.updateGeometriat(any()) } just runs

        val savedHankeGeometria = service.saveGeometriat(geometriat, geometriaId)

        verifySequence {
            geometriatDao.retrieveGeometriat(geometriaId)
            geometriatDao.updateGeometriat(any())
        }
        assertThat(savedHankeGeometria).isNotNull().all {
            prop(Geometriat::version).isEqualTo(1)
            prop(Geometriat::createdAt).isNotNull()
            prop(Geometriat::modifiedAt).isNotNull()
            prop(Geometriat::featureCollection).isNotNull()
            isNotSameAs(geometriat)
        }
    }

    @Test
    fun `save Geometriat OK - without old version`() {
        val geometriat = loadGeometriat()
        geometriat.id = 666
        geometriat.version = null
        geometriat.createdAt = null
        geometriat.modifiedAt = null
        every { geometriatDao.createGeometriat(any()) } answers
            {
                firstArg<Geometriat>().apply { id = 42 }
            }

        val savedHankeGeometria = service.saveGeometriat(geometriat, null)

        verifySequence { geometriatDao.createGeometriat(any()) }
        assertThat(savedHankeGeometria).isNotNull().all {
            prop(Geometriat::id).isEqualTo(42)
            prop(Geometriat::version).isEqualTo(0)
            prop(Geometriat::createdAt).isNotNull()
            prop(Geometriat::modifiedAt).isNull()
            prop(Geometriat::featureCollection).isNotNull()
            isNotSameAs(geometriat)
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

        val savedHankeGeometria = service.saveGeometriat(geometriat, geometriatId)

        verifySequence {
            geometriatDao.retrieveGeometriat(geometriatId)
            geometriatDao.deleteGeometriat(oldGeometriat)
        }
        assertThat(savedHankeGeometria).isNull()
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

        val savedHankeGeometria = service.saveGeometriat(geometriat, geometriatId)

        verify { geometriatDao.retrieveGeometriat(geometriatId) }
        verify { geometriatDao.updateGeometriat(any()) }
        verify(exactly = 0) { geometriatDao.deleteGeometriat(any()) }
        verify(exactly = 0) { geometriatDao.createGeometriat(any()) }
        assertThat(savedHankeGeometria).isNotNull().all {
            prop(Geometriat::version).isEqualTo(2)
            prop(Geometriat::createdAt).isNotNull()
            prop(Geometriat::modifiedAt).isNotNull()
            prop(Geometriat::featureCollection).isNotNull().all {
                transform("features") { it.features }.isNotEmpty()
            }
            isNotSameAs(geometriat)
        }
    }
}
