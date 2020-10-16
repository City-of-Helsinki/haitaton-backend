package fi.hel.haitaton.db

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Connection
import java.sql.DriverManager

private val logger = KotlinLogging.logger { }

@Component
@EnableAutoConfiguration
@RestController
@RequestMapping("/api")

class DbController {
    @Autowired
    lateinit var dbConfigProperties: DbConfigProperties

    @GetMapping("/tablenames")
    fun getAllTables(): Boolean {

        val dbUrl: String = dbConfigProperties.appDatasourceUrl + "?" +
            "user=" + dbConfigProperties.appDatasourceUsername +
            "&password=" + dbConfigProperties.appDatasourcePassword

        val con = DriverManager.getConnection(dbUrl)

        logger.info { "Connected to database: " +
                "${dbConfigProperties.appDatasourceUrl}" }
        return listTables(con)
    }

    fun listTables(connection: Connection): Boolean {
        val sql = "select * from pg_tables where schemaname='public';"
        val response = connection.createStatement().execute(sql)
        return response
    }
}
