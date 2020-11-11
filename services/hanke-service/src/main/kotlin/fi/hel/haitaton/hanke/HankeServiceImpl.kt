package fi.hel.haitaton.hanke

import mu.KotlinLogging
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

class HankeServiceImpl(private val dao: HankeDao) : HankeService {

    var index: Int = 12 //TODO: real hankeId generation

    override fun loadHanke(hankeId: String): Hanke? {
        // TODO
        return Hanke("", true, "Hämeentien perusparannus ja katuvalot", ZonedDateTime.now(), ZonedDateTime.now(), "", 1)
    }

    override fun save(hanke: Hanke): Hanke? {

        if (hanke.hankeId.isNullOrEmpty()) {
            //generate hankeID //TODO: real implementation for hankeid
            hanke.hankeId = "SMTGEN_" + index++;
        } else {
            dao.findHankeByHankeId(hanke.hankeId!!) ?: throw HankeNotFoundException(hanke.hankeId)
        }
        logger.info {
            "Saving  Hanke ${hanke.hankeId}: ${hanke.toJsonString()}"
        }
        val hankeEntity = dao.saveHanke(hanke)
        logger.info {
            "Saved Hanke ${hanke.hankeId}"
        }

        //TODO: return mapped Entity data!!
        return Hanke(hanke.hankeId, true, "Hämeentien perusparannus ja katuvalot tietokannasta", ZonedDateTime.now(), ZonedDateTime.now(), "", 1)
    }

}
