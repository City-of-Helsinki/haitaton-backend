package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class HanketunnusServiceImplTest {

    @Test
    fun newHanketunnus() {
        val repository: IdCounterRepository = mockk()
        val service = HanketunnusServiceImpl(repository)

        every { repository.incrementAndGet(CounterType.HANKETUNNUS.name) }.returns(listOf(IdCounter(CounterType.HANKETUNNUS, 1)))

        val hankeTunnus = service.newHanketunnus()
        val currentYear = ZonedDateTime.now(TZ_UTC).year

        assertThat(hankeTunnus).isEqualTo("HAI${currentYear.toString().substring(2)}-1")
    }
}