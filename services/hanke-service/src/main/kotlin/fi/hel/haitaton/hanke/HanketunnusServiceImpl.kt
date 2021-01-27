package fi.hel.haitaton.hanke

import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

open class HanketunnusServiceImpl(private val idCounterRepository: IdCounterRepository) : HanketunnusService {

    @Transactional(
        readOnly = false,
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED
    )
    override fun newHanketunnus(): String {
        val currentYear = currentYear()
        val nextId = nextId()
        return "${HANKETUNNUS_PREFIX}${currentYear}-${nextId}"
    }

    private fun currentYear(): String {
        return ZonedDateTime.now(TZ_UTC).year.toString().substring(2)
    }

    private fun nextId(): String {
        val idCounter = idCounterRepository.incrementAndGet(CounterType.HANKETUNNUS.name)[0]
        return idCounter.value!!.toString()
    }
}