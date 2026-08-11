package fi.hel.haitaton.hanke.configuration

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import javax.sql.DataSource
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.jdbc.lock.DefaultLockRepository
import org.springframework.integration.jdbc.lock.JdbcLockRegistry
import org.springframework.integration.jdbc.lock.LockRepository
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Configuration
@Profile("!test")
class LockRepositories {
    /** The time to hold on to dead locks. */
    private val timeToLive = Duration.ofMinutes(15)

    @Bean
    fun defaultLockRepository(dataSource: DataSource): DefaultLockRepository {
        return DefaultLockRepository(dataSource)
    }

    @Bean
    fun jdbcLockRegistry(lockRepository: LockRepository): JdbcLockRegistry {
        return JdbcLockRegistry(lockRepository, timeToLive)
    }
}

@Service
@Profile("!test")
class LockService(private val jdbcLockRegistry: JdbcLockRegistry) {

    /** Run the given function if a lock is obtained. If the lock is not obtained, do nothing. */
    fun doIfUnlocked(name: String, f: () -> Unit) {
        val lock = jdbcLockRegistry.obtain(name)
        if (lock.tryLock()) {
            withLock(lock, name, f)
        } else {
            logger.info("Lock was already reserved, name = $name")
        }
    }

    /** Waits until the lock becomes available. */
    fun withLock(name: String, time: Long, unit: TimeUnit, f: () -> Unit) {
        val lock = jdbcLockRegistry.obtain(name)
        if (lock.tryLock(time, unit)) {
            withLock(lock, name, f)
        } else {
            logger.error("Lock was not obtained after waiting $time $unit, name = $name")
        }
    }

    private fun withLock(lock: Lock, name: String, f: () -> Unit) {
        logger.info("Lock obtained, name = $name")
        try {
            f()
        } catch (e: Exception) {
            logger.error(e) { "Exception while holding lock, name = $name" }
        } finally {
            lock.unlock()
            logger.info("Lock released, name = $name")
        }
    }
}
