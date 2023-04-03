package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.Alikontakti
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YHTEISO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.getCurrentTimeUTC

object HankeYhteystietoFactory {

    /** Create a test yhteystieto with values in all fields. */
    fun create(id: Int? = 1, organisaatioId: Int? = 1): HankeYhteystieto {
        return HankeYhteystieto(
            id = id,
            nimi = "Teppo Testihenkilö",
            email = "teppo@example.test",
            puhelinnumero = "04012345678",
            organisaatioId = organisaatioId,
            organisaatioNimi = "Organisaatio",
            osasto = "Osasto",
            createdBy = "test7358",
            createdAt = getCurrentTimeUTC(),
            modifiedBy = "test7358",
            modifiedAt = getCurrentTimeUTC(),
            rooli = "Isännöitsijä",
            tyyppi = YRITYS,
            alikontaktit =
                listOf(Alikontakti("Ali", "Kontakti", "ali.kontakti@meili.com", "050-4567890"))
        )
    }

    /**
     * Create a new Yhteystieto with values differentiated by the given integer. The audit and id
     * fields are left null.
     */
    fun createDifferentiated(intValue: Int): HankeYhteystieto {
        return HankeYhteystieto(
            id = null,
            nimi = "etu$intValue suku$intValue",
            email = "email$intValue",
            puhelinnumero = "010$intValue$intValue$intValue$intValue$intValue$intValue$intValue",
            organisaatioId = intValue,
            organisaatioNimi = "org$intValue",
            osasto = "osasto$intValue",
            rooli = "Isännöitsijä$intValue",
            tyyppi = YHTEISO,
            alikontaktit =
                listOf(
                    Alikontakti(
                        sukunimi = "suku$intValue",
                        etunimi = "etu$intValue",
                        email = "email$intValue",
                        puhelinnumero =
                            "010$intValue$intValue$intValue$intValue$intValue$intValue$intValue",
                    )
                )
        )
    }

    /**
     * Create a list of test yhteystiedot. The values of the created yhteystiedot are differentiated
     * by the given integers.
     *
     * You can provide a lambda for mutating the generated yhteystieto after creation.
     */
    fun createDifferentiated(
        intValues: List<Int>,
        mutator: (HankeYhteystieto) -> Unit = {}
    ): MutableList<HankeYhteystieto> =
        intValues.map { createDifferentiated(it).apply(mutator) }.toMutableList()
}
