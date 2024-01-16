package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YHTEISO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.DEFAULT_YTUNNUS
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.time.ZonedDateTime

object HankeYhteystietoFactory {

    const val DEFAULT_YTUNNUS = "1817548-2"

    /** Create a test yhteystieto with values in all fields. */
    fun create(
        id: Int? = 1,
        nimi: String = "Teppo Testihenkilö",
        email: String = TEPPO_EMAIL,
        tyyppi: YhteystietoTyyppi = YRITYS,
        ytunnus: String = DEFAULT_YTUNNUS,
        puhelinnumero: String = "04012345678",
        createdAt: ZonedDateTime? = getCurrentTimeUTC(),
        modifiedAt: ZonedDateTime? = getCurrentTimeUTC(),
    ): HankeYhteystieto {
        return HankeYhteystieto(
            id = id,
            nimi = nimi,
            email = email,
            tyyppi = tyyppi,
            ytunnus = if (tyyppi != YKSITYISHENKILO) ytunnus else null,
            puhelinnumero = puhelinnumero,
            organisaatioNimi = "Organisaatio",
            osasto = "Osasto",
            createdBy = "test7358",
            createdAt = createdAt,
            modifiedBy = "test7358",
            modifiedAt = modifiedAt,
            rooli = "Isännöitsijä",
            yhteyshenkilot = listOf(),
        )
    }

    fun createEntity(
        id: Int = 1,
        contactType: ContactType,
        hanke: HankeEntity
    ): HankeYhteystietoEntity =
        with(create()) {
            HankeYhteystietoEntity(
                    id = id,
                    contactType = contactType,
                    nimi = "$nimi $contactType",
                    email = "$contactType.$email",
                    tyyppi = tyyppi,
                    ytunnus = ytunnus,
                    puhelinnumero = puhelinnumero,
                    organisaatioNimi = organisaatioNimi,
                    osasto = osasto,
                    rooli = rooli,
                    dataLocked = false,
                    dataLockInfo = "info",
                    createdByUserId = createdBy,
                    createdAt = createdAt?.toLocalDateTime(),
                    modifiedByUserId = modifiedBy,
                    modifiedAt = modifiedAt?.toLocalDateTime(),
                    hanke = hanke,
                )
                .apply {
                    yhteyshenkilot =
                        mutableListOf(
                            HankeYhteyshenkiloFactory.createEntity(id * 2, this),
                            HankeYhteyshenkiloFactory.createEntity(id * 2 + 1, this)
                        )
                }
        }

    /**
     * Create a new Yhteystieto with values differentiated by the given integer. The audit and id
     * fields are left null.
     */
    fun createDifferentiated(
        i: Int,
        id: Int? = i,
        yhteyshenkilot: List<Yhteyshenkilo> = listOf()
    ): HankeYhteystieto {
        return HankeYhteystieto(
            id = id,
            nimi = "etu$i suku$i",
            email = "email$i",
            ytunnus = DEFAULT_YTUNNUS,
            puhelinnumero = dummyPhoneNumber(i),
            organisaatioNimi = "org$i",
            osasto = "osasto$i",
            rooli = "Isännöitsijä$i",
            tyyppi = YHTEISO,
            yhteyshenkilot = yhteyshenkilot,
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
        mutator: HankeYhteystieto.() -> Unit = {}
    ): MutableList<HankeYhteystieto> =
        intValues.map { createDifferentiated(it).apply(mutator) }.toMutableList()

    private fun dummyPhoneNumber(i: Int) = "010$i$i$i$i$i$i$i"
}

fun MutableList<HankeYhteystieto>.modify(
    ytunnus: String? = DEFAULT_YTUNNUS,
    tyyppi: YhteystietoTyyppi? = YRITYS
) = map { it.copy(ytunnus = ytunnus, tyyppi = tyyppi) }.toMutableList()
