package fi.hel.haitaton.hanke.security

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class UserSessionCleanupSchedulingConfigurer(
    private val userSessionService: UserSessionService,
    private val properties: UserSessionCleanupProperties,
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedDelayTask(
            { userSessionService.cleanupExpiredSessions(properties.interval.seconds) },
            properties.interval,
        )
    }
}
