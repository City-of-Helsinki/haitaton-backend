package fi.hel.haitaton.hello.db

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DbConfigProperties {

    @Value("\${app.datasource.url}")
    lateinit var appDatasourceUrl: String

    @Value("\${app.datasource.username}")
    lateinit var appDatasourceUsername: String

    @Value("\${app.datasource.password}")
    lateinit var appDatasourcePassword: String
}
