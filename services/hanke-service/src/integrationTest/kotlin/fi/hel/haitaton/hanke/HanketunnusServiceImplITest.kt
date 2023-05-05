package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HanketunnusServiceImplITest : DatabaseTest() {

    @Autowired lateinit var hanketunnusService: HanketunnusService

    @Test
    fun newHanketunnus() {
        val hanketunnus1 = hanketunnusService.newHanketunnus()
        val hanketunnus2 = hanketunnusService.newHanketunnus()

        val serial1 = hanketunnus1.substringAfterLast('-').toInt()
        val serial2 = hanketunnus2.substringAfterLast('-').toInt()
        // hanketunnus pattern is HAIYY-N where YY is the current year (2 last digits)
        // and N is a increasing serial number
        assertThat(serial1).isLessThan(serial2)
    }
}
