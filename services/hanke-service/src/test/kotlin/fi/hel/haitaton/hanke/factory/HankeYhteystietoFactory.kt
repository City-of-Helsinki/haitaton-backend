package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.Yhteyshenkilo
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
                listOf(Yhteyshenkilo("Ali", "Kontakti", "ali.kontakti@meili.com", "050-4567890"))
        )
    }

    /**
     * Create a new Yhteystieto with values differentiated by the given integer. The audit and id
     * fields are left null.
     */
    fun createDifferentiated(i: Int): HankeYhteystieto {
        return HankeYhteystieto(
            id = null,
            nimi = "etu$i suku$i",
            email = "email$i",
            puhelinnumero = "010$i$i$i$i$i$i$i",
            organisaatioId = i,
            organisaatioNimi = "org$i",
            osasto = "osasto$i",
            rooli = "Isännöitsijä$i",
            tyyppi = YHTEISO,
            alikontaktit =
                listOf(
                    Yhteyshenkilo(
                        sukunimi = "yhteys-suku$i",
                        etunimi = "yhteys-etu$i",
                        email = "yhteys-email$i",
                        puhelinnumero = dummyPhoneNumber(i),
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

    private fun dummyPhoneNumber(i: Int) = "010$i$i$i$i$i$i$i"
}
