package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeEntity
import org.springframework.jdbc.core.JdbcOperations

class HankeGeometriatDaoImpl(private val jdbcOperations: JdbcOperations) : HankeGeometriatDao {
    override fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometriat: HankeGeometriat) {
        TODO("Not yet implemented")
    }

    override fun loadHankeGeometria(hankeEntity: HankeEntity): HankeGeometriat? {
        TODO("Not yet implemented")
    }
}
