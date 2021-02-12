package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.toJsonPrettyString
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/*
 A manual test for TormaystarkasteluDaoImpl.
 NOTICE! You need db (in docker-compose.yml) running during the test and the "tormays" data loaded into it.
 */
internal class TormaystarkasteluDaoImplManualTest {

    companion object {
        fun dataSource(): DataSource {
            return PGSimpleDataSource().apply {
                // these values are ONLY for local docker-compose-based testing. Do not change here any other values (or at least do not commit those other values)
                this.setURL("jdbc:postgresql://localhost/haitaton")
                this.user = "haitaton_user"
                this.password = "haitaton"
            }
        }
    }

    private val dao = TormaystarkasteluDaoImpl(JdbcTemplate(dataSource()))

    /*
    Test manually whether Hanke geometries are located on "yleinen katualue"
     */
    @Test
    fun yleisetKatualueet() {
        val yleisetKatualueet = dao.yleisetKatualueet(1)
        println(yleisetKatualueet.toJsonPrettyString())
    }

    /*
    Test manually what kind of cycleways Hanke geometries are located on
     */
    @Test
    fun pyorailyreitit() {
        val pyorailyreitit = dao.pyorailyreitit(1)
        println(pyorailyreitit.toJsonPrettyString())
    }
}