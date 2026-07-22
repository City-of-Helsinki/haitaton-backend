package fi.hel.haitaton.hanke.configuration

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.IntegrationTest
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
}
