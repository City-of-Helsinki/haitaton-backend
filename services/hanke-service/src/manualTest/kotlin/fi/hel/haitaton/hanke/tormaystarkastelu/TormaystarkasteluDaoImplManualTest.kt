package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.toJsonPrettyString
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/*
 A manual test for TormaystarkasteluDaoImpl.
 NOTICE! You need PostgreSQL running during the test.
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
    Test manually what kind of cycleways are overlapping specific Hanke geometries
     */
    @Test
    fun pyorailyreitit() {
        val pyorailyreitit = dao.pyorailyreitit(1)
        println(pyorailyreitit.toJsonPrettyString())
    }
}