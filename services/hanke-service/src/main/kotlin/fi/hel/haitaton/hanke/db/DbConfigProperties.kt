package fi.hel.haitaton.hanke.db

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DbConfigProperties {

    @Value("\${spring.datasource.url}")
    lateinit var appDatasourceUrl: String

    @Value("\${spring.datasource.username}")
    lateinit var appDatasourceUsername: String

    @Value("\${spring.datasource.password}")
    lateinit var appDatasourcePassword: String
}
