package fi.hel.haitaton.hanke

import java.time.ZonedDateTime

class HankeServiceImpl : HankeService {
    override fun loadHanke(hankeId: String): Hanke? {
        // TODO
        return Hanke("", true, "Hämeentien perusparannus ja katuvalot", ZonedDateTime.now(), ZonedDateTime.now(), "", 1)
    }

    override fun save(hanke: Hanke): Hanke? {
        // TODO
        return Hanke("", true, "Hämeentien perusparannus ja katuvalot", ZonedDateTime.now(), ZonedDateTime.now(), "", 1)
    }

}
