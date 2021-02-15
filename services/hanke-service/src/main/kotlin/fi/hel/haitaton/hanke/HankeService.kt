package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch

interface HankeService {

    /**
     * Fetch hanke with hankeTunnus.
     * Returns null if there is no hanke with the given tunnus.
     */
    fun loadHanke(hankeTunnus: String): Hanke?

    fun createHanke(hanke: Hanke): Hanke

    fun updateHanke(hanke: Hanke): Hanke

    /**
     * Meant for internal use only (do not reveal in controller or other public end-point).
     * Only saves the flag-fields from the given hanke-object, and caller needs to
     * make sure the flags in the given hanke instance have not come from "outside".
     * Does not change version or modifiedAt/By fields, and does not return
     * anything.
     */
    fun updateHankeStateFlags(hanke: Hanke)

    fun loadAllHanke(hankeSearch: HankeSearch? = null): List<Hanke>

}
