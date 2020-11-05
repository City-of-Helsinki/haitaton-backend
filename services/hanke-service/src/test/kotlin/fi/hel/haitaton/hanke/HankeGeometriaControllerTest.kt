package fi.hel.haitaton.hanke

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.*
import org.geojson.FeatureCollection
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

internal class HankeGeometriaControllerTest {

    private val service = mockk<HankeService>()

    private val controller = HankeGeometriaController(service)

    @Test
    fun `create Geometria OK`() {
        val hankeId = "1234567"
        every { service.saveGeometria(hankeId, any()) } just runs

        val response = controller.createGeometria(hankeId, FeatureCollection())

        verify { service.saveGeometria(hankeId, any()) }
        assertAll {
            assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(response.body).isNull()
        }
    }

    @Test
    fun `create Geometria with missing Hanke`() {
        val hankeId = "1234567"
        every { service.saveGeometria(hankeId, any()) } throws HankeNotFoundException(hankeId)

        val response = controller.createGeometria(hankeId, FeatureCollection())

        verify { service.saveGeometria(hankeId, any()) }
        assertAll {
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(response.body).transform { it.toString() }.contains("HAI1001")
        }
    }

    @Test
    fun `create Geometria with service failure`() {
        val hankeId = "1234567"
        every { service.saveGeometria(hankeId, any()) } throws RuntimeException("Something went wrong")

        val response = controller.createGeometria(hankeId, FeatureCollection())

        verify { service.saveGeometria(hankeId, any()) }
        assertAll {
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            assertThat(response.body).transform { it.toString() }.contains("HAI1012")
        }
    }

    @Test
    fun `create Geometria with missing Geometria`() {
        val hankeId = "1234567"

        val response = controller.createGeometria(hankeId, null)

        assertAll {
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).transform { it.toString() }.contains("HAI1011")
        }
    }
}