package fi.hel.haitaton.hanke

import java.time.ZonedDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HanketunnusService(private val idCounterRepository: IdCounterRepository) {

    @Transactional(
        readOnly = false,
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED
    )
    fun newHanketunnus(): String {
        val currentYear = currentYear()
        val nextId = nextId()
        return "${HANKETUNNUS_PREFIX}$currentYear-$nextId"
    }

    private fun currentYear(): String {
        return ZonedDateTime.now(TZ_UTC).year.toString().substring(2)
    }

    private fun nextId(): String {
        val idCounter = idCounterRepository.incrementAndGet(CounterType.HANKETUNNUS.name)[0]
        return idCounter.value!!.toString()
    }
}
