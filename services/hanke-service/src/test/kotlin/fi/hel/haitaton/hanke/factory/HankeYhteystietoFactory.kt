package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.getCurrentTimeUTC

object HankeYhteystietoFactory {

    /** Create a test yhteystieto with values in all fields. */
    fun create(): HankeYhteystieto {
        return HankeYhteystieto(
            id = 1,
            sukunimi = "Testihenkilö",
            etunimi = "Teppo",
            email = "teppo@example.test",
            puhelinnumero = "1234",
            organisaatioId = 1,
            organisaatioNimi = "Organisaatio",
            osasto = "Osasto",
            createdBy = "test7358",
            createdAt = getCurrentTimeUTC(),
            modifiedBy = "test7358",
            modifiedAt = getCurrentTimeUTC()
        )
    }

    /**
     * Create a new Yhteystieto with values differentiated by the given integer. The audit and id
     * fields are left null.
     */
    fun createDifferentiated(intValue: Int): HankeYhteystieto {
        return HankeYhteystieto(
            null,
            "suku$intValue",
            "etu$intValue",
            "email$intValue",
            "010$intValue$intValue$intValue$intValue$intValue$intValue$intValue",
            intValue,
            "org$intValue",
            "osasto$intValue"
        )
    }

    /**
     * Create a list of test yhteystiedot. The values of the created yhteystiedot are differentiated
     * by the given integers.
     */
    fun createDifferentiated(intValues: List<Int>): MutableList<HankeYhteystieto> =
        intValues.map { createDifferentiated(it) }.toMutableList()
}
