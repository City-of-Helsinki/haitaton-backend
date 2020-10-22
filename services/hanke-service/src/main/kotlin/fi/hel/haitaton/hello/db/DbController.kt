package fi.hel.haitaton.hello.db

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Connection
import java.sql.DriverManager
import java.util.ArrayList


@Component
@RestController
@RequestMapping("/api")

class DbController {
    private val logger = KotlinLogging.logger { }

    @Autowired
    lateinit var dbConfigProperties: DbConfigProperties

    @GetMapping("/tablenames")
    fun getAllTables(): String {

        val dbUrl: String = dbConfigProperties.appDatasourceUrl + "?" +
                "user=" + dbConfigProperties.appDatasourceUsername +
                "&password=" + dbConfigProperties.appDatasourcePassword

        val con = DriverManager.getConnection(dbUrl)

        logger.info {
            "Connected to database: " +
                "${dbConfigProperties.appDatasourceUrl}" }
        return listTables(con).toString()
    }

    fun listTables(connection: Connection): ArrayList<String> {
        var listOfTableNames: ArrayList<String> = arrayListOf()
        val sql = "select * from pg_tables where schemaname='public';"
        val response = connection.createStatement().executeQuery(sql)
        while (response.next()) {
            listOfTableNames.add(response.getString("tablename"))
        }
        logger.info { "query response:" + "$listOfTableNames" }
        return listOfTableNames
    }
}
