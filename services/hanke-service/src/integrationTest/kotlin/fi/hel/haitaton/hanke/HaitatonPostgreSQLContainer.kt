package fi.hel.haitaton.hanke

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object HaitatonPostgreSQLContainer : PostgreSQLContainer<HaitatonPostgreSQLContainer>(
    DockerImageName.parse("postgis/postgis:13-master").asCompatibleSubstituteFor("postgres")
) {

    override fun start() {
        super.start()
        System.setProperty("DB_URL", jdbcUrl)
        System.setProperty("DB_USERNAME", username)
        System.setProperty("DB_PASSWORD", password)
    }

    override fun stop() {
        // do nothing, JVM handles shut down
    }
}
