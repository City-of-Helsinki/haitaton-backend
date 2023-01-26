package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue

/**
 * Methods for checking whether a hanke is complete or a draft. A hanke is considered complete if
 * all mandatory fields are filled.
 */
object CompleteHankeValidator {

    fun isHankeComplete(hanke: Hanke): Boolean = !isHankeDraft(hanke)

    private fun isHankeDraft(hanke: Hanke): Boolean {
        return hanke.nimi.isNullOrBlank() ||
            hanke.kuvaus.isNullOrBlank() ||
            hanke.tyomaaKatuosoite.isNullOrBlank() ||
            hanke.vaihe == null ||
            (hanke.vaihe == Vaihe.SUUNNITTELU && hanke.suunnitteluVaihe == null) ||
            hanke.alueet.size == 0 ||
            hanke.alueet.any { incompleteAlue(it) } ||
            hanke.omistajat.size == 0 ||
            hanke.omistajat.any { incompleteYhteystieto(it) } ||
            hanke.arvioijat.any { incompleteYhteystieto(it) } ||
            hanke.toteuttajat.any { incompleteYhteystieto(it) } ||
            hanke.tormaystarkasteluTulos == null
    }

    private fun incompleteAlue(alue: Hankealue): Boolean {
        return alue.haittaAlkuPvm == null ||
            alue.haittaLoppuPvm == null ||
            alue.meluHaitta == null ||
            alue.polyHaitta == null ||
            alue.tarinaHaitta == null ||
            alue.kaistaHaitta == null ||
            alue.kaistaPituusHaitta == null ||
            alue.geometriat?.featureCollection?.features?.isEmpty() != false
    }

    /**
     * Mandatory fields after Yhteystiedot have been redone:
     * - Tyyppi
     * - Nimi
     * - Y-tunnus tai henkilötunnus
     * - Email
     *
     * For cantacs the mandatory fields are:
     * - Nimi
     * - Email
     */
    private fun incompleteYhteystieto(yhteystieto: HankeYhteystieto): Boolean {
        return yhteystieto.etunimi.isBlank() ||
            yhteystieto.sukunimi.isBlank() ||
            yhteystieto.email.isBlank()
    }
}
