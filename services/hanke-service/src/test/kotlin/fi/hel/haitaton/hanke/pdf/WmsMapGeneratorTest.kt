package fi.hel.haitaton.hanke.pdf

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.getResourceAsText
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import net.pwall.mustache.Template
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.geotools.ows.wms.WebMapServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WmsMapGeneratorTest {

    private val wideArea: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/wide-area.json".asJsonResource()
    private val tallArea: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/tall-area.json".asJsonResource()
    private val manyAreas: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/many-areas.json".asJsonResource()
    private val hankealueet: List<SavedHankealue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/hankealueet.json".asJsonResource()

    @Nested
    inner class MapWithAreas {
        private lateinit var mockWmsServer: MockWebServer

        private lateinit var wms: WebMapServer
        private lateinit var mapGenerator: WmsMapGenerator

        @BeforeEach
        fun setup() {
            mockWmsServer = MockWebServer()
            mockWmsServer.start()
            mockWmsServer.dispatcher = GeoserverDispatcher(mockWmsServer.url(""))

            wms = WebMapServer(mockWmsServer.url("/capabilities").toUrl())
            mapGenerator = WmsMapGenerator(wms)
        }

        @AfterEach
        fun tearDown() {
            mockWmsServer.shutdown()
        }

        @Test
        fun `calls the map server with the correct parameters`() {
            mapGenerator.mapWithAreas(wideArea, hankealueet, 1000, 500) { _ -> null }

            assertThat(mockWmsServer.requestCount).isEqualTo(2)
            val capabilityRequest = mockWmsServer.takeRequest()
            assertThat(capabilityRequest).all {
                prop(RecordedRequest::path)
                    .isNotNull()
                    .contains(
                        "/capabilities",
                        "REQUEST=GetCapabilities",
                        "VERSION=1.3.0",
                        "SERVICE=WMS",
                    )
            }
            val mapRequest = mockWmsServer.takeRequest()
            assertThat(mapRequest).all {
                prop(RecordedRequest::path)
                    .isNotNull()
                    .contains(
                        "/image", // The URL is read from the capabilities XML.
                        "REQUEST=GetMap",
                        "FORMAT=image/png",
                        "CRS=EPSG:3879",
                        // Check CalculateBounds tests for where these bounds come from.
                        "BBOX=6672690.0,2.549634E7,6673410.0,2.549706E7",
                        "VERSION=1.3.0",
                        "STYLES=default-style-avoindata%3AKiinteistokartan_maastotiedot",
                        "SERVICE=WMS",
                        "WIDTH=1000",
                        "HEIGHT=1000",
                        "TRANSPARENT=TRUE",
                        "LAYERS=avoindata%3AKiinteistokartan_maastotiedot",
                    )
            }
        }

        @Test
        fun `returns an image of the requested size`() {
            val image = mapGenerator.mapWithAreas(wideArea, hankealueet, 1000, 500) { _ -> null }

            val png = ImageIO.read(ByteArrayInputStream(image))
            assertThat(png.width).isEqualTo(1000)
            assertThat(png.height).isEqualTo(500)
        }
    }

    @Nested
    inner class CalculateBounds {

        @Test
        fun `returns correct bounds when fitting a wide area to a wide image`() {
            val kaivuilmoitusalueet: List<KaivuilmoitusAlue> =
                "/fi/hel/haitaton/hanke/pdf-test-data/wide-area.json".asJsonResource()

            val result = WmsMapGenerator.calculateBounds(kaivuilmoitusalueet, 1000, 500)

            assertThat(result).all {
                prop(MapBounds::xSize).isEqualTo(720.0)
                prop(MapBounds::ySize).isEqualTo(720.0)
                prop(MapBounds::center).all {
                    prop(Point::x).isEqualTo(25496700.0)
                    prop(Point::y).isEqualTo(6673050.0)
                }
                prop(MapBounds::min).all {
                    // Width is padded by 10%.
                    // Image and area are both wide, so no adjustment here.
                    prop(Point::x).isEqualTo(25496400.0 - 0.1 * 600.0)
                    prop(Point::x).isEqualTo(25496340.0)
                    // Y-coordinates are expanded so the height matches the width.
                    prop(Point::y).isEqualTo(result.center.y - (600 + 2 * 60) / 2)
                    prop(Point::y).isEqualTo(6672690.0)
                }
                prop(MapBounds::max).all {
                    prop(Point::x).isEqualTo(25497000.0 + 0.1 * 600.0)
                    prop(Point::x).isEqualTo(25497060.0)
                    prop(Point::y).isEqualTo(result.center.y + (600 + 2 * 60) / 2)
                    prop(Point::y).isEqualTo(6673410.0)
                }
            }
        }

        @Test
        fun `returns correct bounds when fitting a wide area to a tall image`() {
            val result = WmsMapGenerator.calculateBounds(wideArea, 500, 1000)

            assertThat(result).all {
                // The area is wide. With padding, it's 720 wide.
                // The image is tall, so the y-coordinates are expanded so the height is double
                // (1000 / 500) the width, so the height is set at 1440.
                // The x-coordinates are then expanded so that the width equals the height.
                prop(MapBounds::xSize).isEqualTo(1440.0)
                prop(MapBounds::ySize).isEqualTo(1440.0)
                prop(MapBounds::center).all {
                    prop(Point::x).isEqualTo(25496700.0)
                    prop(Point::y).isEqualTo(6673050.0)
                }
                prop(MapBounds::min).all {
                    prop(Point::x).isEqualTo(result.center.x - result.xSize / 2)
                    prop(Point::x).isEqualTo(25495980.0)
                    prop(Point::y).isEqualTo(result.center.y - result.ySize / 2)
                    prop(Point::y).isEqualTo(6672330.0)
                }
                prop(MapBounds::max).all {
                    prop(Point::x).isEqualTo(result.center.x + result.xSize / 2)
                    prop(Point::x).isEqualTo(25497420.0)
                    prop(Point::y).isEqualTo(result.center.y + result.ySize / 2)
                    prop(Point::y).isEqualTo(6673770.0)
                }
            }
        }

        @Test
        fun `returns correct bounds when fitting a tall area to a wide image`() {
            val result = WmsMapGenerator.calculateBounds(tallArea, 1000, 500)

            assertThat(result).all {
                // The area is tall. With padding, it's 260 tall.
                // The image is wide, so the x-coordinates are expanded so the width is double
                // (1000 / 500) the height, so the width is set at 520.
                // The y-coordinates are then expanded so that the height equals the width.
                prop(MapBounds::xSize).isEqualTo(520.0)
                prop(MapBounds::ySize).isEqualTo(520.0)
                prop(MapBounds::center).all {
                    prop(Point::x).isEqualTo(25497275.0)
                    prop(Point::y).isEqualTo(6673000.0)
                }
                prop(MapBounds::min).all {
                    prop(Point::x).isEqualTo(result.center.x - result.xSize / 2)
                    prop(Point::x).isEqualTo(25497015.0)
                    prop(Point::y).isEqualTo(result.center.y - result.ySize / 2)
                    prop(Point::y).isEqualTo(6672740.0)
                }
                prop(MapBounds::max).all {
                    prop(Point::x).isEqualTo(result.center.x + result.xSize / 2)
                    prop(Point::x).isEqualTo(25497535.0)
                    prop(Point::y).isEqualTo(result.center.y + result.ySize / 2)
                    prop(Point::y).isEqualTo(6673260.0)
                }
            }
        }

        @Test
        fun `returns correct bounds when fitting a tall area to a tall image`() {
            val result = WmsMapGenerator.calculateBounds(tallArea, 500, 1000)

            assertThat(result).all {
                prop(MapBounds::xSize).isEqualTo(260.0)
                prop(MapBounds::ySize).isEqualTo(260.0)
                prop(MapBounds::center).all {
                    prop(Point::x).isEqualTo(25497275.0)
                    prop(Point::y).isEqualTo(6673000.0)
                }
                prop(MapBounds::min).all {
                    // x-coordinates are expanded so the width matches the height.
                    prop(Point::x).isEqualTo(result.center.x - result.xSize / 2)
                    prop(Point::x).isEqualTo(25497145.0)
                    // The y-coordinates are padded with the minimum padding of 30.
                    // Image and area are both tall, so no adjustment here.
                    prop(Point::y).isEqualTo(6672900.0 - 30)
                    prop(Point::y).isEqualTo(6672870.0)
                }
                prop(MapBounds::max).all {
                    prop(Point::x).isEqualTo(result.center.x + result.xSize / 2)
                    prop(Point::x).isEqualTo(25497405.0)
                    prop(Point::y).isEqualTo(6673100.0 + 30)
                    prop(Point::y).isEqualTo(6673130.0)
                }
            }
        }

        @Test
        fun `returns correct bounds when there are many areas`() {
            val result = WmsMapGenerator.calculateBounds(manyAreas, 1000, 500)

            assertThat(result).all {
                // Y-direction is the one dictating the image scale. The height of the extremes
                // (6672760 and 6673002) of the areas is 242, so 302 with padding.
                // This gives us a width of 302 * (1000 / 500) = 604.
                // The height is then expanded to match the width.
                prop(MapBounds::xSize).isEqualTo(604.0)
                prop(MapBounds::ySize).isEqualTo(604.0)
                prop(MapBounds::center).all {
                    prop(Point::x).isEqualTo(25497022.0)
                    prop(Point::y).isEqualTo(6672881.0)
                }
                prop(MapBounds::min).all {
                    prop(Point::x).isEqualTo(result.center.x - result.xSize / 2)
                    prop(Point::x).isEqualTo(25496720.0)
                    prop(Point::y).isEqualTo(result.center.y - result.ySize / 2)
                    prop(Point::y).isEqualTo(6672579.0)
                }
                prop(MapBounds::max).all {
                    prop(Point::x).isEqualTo(result.center.x + result.xSize / 2)
                    prop(Point::x).isEqualTo(25497324.0)
                    prop(Point::y).isEqualTo(result.center.y + result.ySize / 2)
                    prop(Point::y).isEqualTo(6673183.0)
                }
            }
        }
    }

    class GeoserverDispatcher(private val baseUrl: HttpUrl) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            return request.path?.let { path ->
                if (path.startsWith("/capabilities")) {
                    capabilityResponse()
                } else if (path.startsWith("/image")) {
                    imageResponse()
                } else null
            } ?: MockResponse().setResponseCode(404)
        }

        private fun capabilityResponse(): MockResponse {
            val responseTemplate =
                Template.parse(
                    "/fi/hel/haitaton/hanke/pdf-test-data/capabilities.xml".getResourceAsText()
                )
            val params = mapOf("baseUrl" to baseUrl)

            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.ogc.wms_xml")
                .setBody(responseTemplate.processToString(params))
        }

        private fun imageResponse(): MockResponse {
            val bytes = "/fi/hel/haitaton/hanke/pdf-test-data/blank.png".getResourceAsBytes()

            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(bytes))
        }
    }
}
