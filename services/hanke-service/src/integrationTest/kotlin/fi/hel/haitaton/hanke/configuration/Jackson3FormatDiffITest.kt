package fi.hel.haitaton.hanke.configuration

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

class Jackson3FormatDiffITest(@Autowired val jsonMapper: JsonMapper) : IntegrationTest() {

    @Test
    fun `the auto-configured Jackson 3 JsonMapper bean is present`() {
        assertThat(jsonMapper).isNotNull()
    }

    @Test
    fun `use-jackson2-defaults makes Jackson 3 write dates as ISO-8601 strings, not timestamps`() {
        val value = OffsetDateTime.of(2026, 7, 22, 10, 0, 0, 0, ZoneOffset.UTC)

        val json = jsonMapper.writeValueAsString(value)

        assertThat(json).contains("2026-07-22")
    }

    @Test
    fun `Hanke's ZonedDateTime fields serialize identically under Jackson 2 and Jackson 3`() {
        val hanke = HankeFactory.create()

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(hanke)
        val jackson3Json = jsonMapper.writeValueAsString(hanke)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
    }

    @Test
    fun `attachment metadata's OffsetDateTime field serializes identically under Jackson 2 and Jackson 3`() {
        val dto =
            ApplicationAttachmentMetadataDto(
                id = UUID.randomUUID(),
                fileName = "test.pdf",
                contentType = "application/pdf",
                size = 1234L,
                attachmentType = ApplicationAttachmentType.MUU,
                createdByUserId = "test-user",
                createdAt = OffsetDateTime.of(2026, 7, 22, 10, 0, 0, 0, ZoneOffset.UTC),
                applicationId = 1L,
            )

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(dto)
        val jackson3Json = jsonMapper.writeValueAsString(dto)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
    }

    @Test
    fun `a Polygon serialized by Jackson 2 deserializes and re-serializes identically via Jackson 3`() {
        val polygon = GeometriaFactory.polygon()

        val jackson2Json = OBJECT_MAPPER.writeValueAsString(polygon)
        val roundTripped = jsonMapper.readValue(jackson2Json, org.geojson.Polygon::class.java)
        val jackson3Json = jsonMapper.writeValueAsString(roundTripped)

        assertThat(jackson3Json).isEqualTo(jackson2Json)
    }
}
