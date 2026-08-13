package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HankeErrorTest {

    @Test
    fun testJacksonSerialization() {
        val err = HankeError.HAI0002
        val expected = """{"errorMessage":"${err.errorMessage}","errorCode":"${err.errorCode}"}"""
        // Structural, not textual, comparison: JSON object member order carries no meaning per
        // RFC 8259, and Jackson 3's bean introspection doesn't reproduce Jackson 2's property
        // order for this class (errorMessage is a constructor param, errorCode a plain getter).
        assertThat(OBJECT_MAPPER.readTree(err.toJsonString())).isEqualTo(OBJECT_MAPPER.readTree(expected))
    }
}
