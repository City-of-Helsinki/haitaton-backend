package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HankeErrorTest {

    @Test
    fun testJacksonSerialization() {
        val err = HankeError.HAI0002
        val expected = """{"errorMessage":"${err.errorMessage}","errorCode":"${err.errorCode}"}"""
        assertThat(err.toJsonString()).isEqualTo(expected)
    }
}
