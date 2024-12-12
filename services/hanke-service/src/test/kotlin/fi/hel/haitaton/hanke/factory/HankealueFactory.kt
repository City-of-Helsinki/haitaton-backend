package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.Autoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.HaittaAjanKestoLuokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Liikennemaaraluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluKatuluokka
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime

object HankealueFactory {

    val TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU =
        Autoliikenneluokittelu(
            HaittaAjanKestoLuokittelu.YLI_KOLME_KUUKAUTTA.value,
            TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS.value,
            Liikennemaaraluokittelu.LIIKENNEMAARA_ALLE_500.value,
            VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE.value,
            AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA.value,
        )

    val TORMAYSTARKASTELU_ZERO_AUTOLIIKENNELUOKITTELU =
        Autoliikenneluokittelu(
            1,
            0, // if both 'katuluokka' and 'liikennemaara' is 0, the index is 0.0
            0, // if both 'katuluokka' and 'liikennemaara' is 0, the index is 0.0
            1,
            1,
        )

    val DEFAULT_HHS_YLEINEN = "Yleisten haittojen hallintasuunnitelma"
    val DEFAULT_HHS_PYORALIIKENNE = "Pyöräliikenteelle koituvien haittojen hallintasuunnitelma"
    val DEFAULT_HHS_AUTOLIIKENNE = "Autoliikenteelle koituvien haittojen hallintasuunnitelma"
    val DEFAULT_HHS_LINJAAUTOLIIKENNE =
        "Linja-autoliikenteelle koituvien haittojen hallintasuunnitelma"
    val DEFAULT_HHS_RAITIOLIIKENNE = "Raitioliikenteelle koituvien haittojen hallintasuunnitelma"
    val DEFAULT_HHS_MUUT = "Muiden haittojen hallintasuunnitelma"

    val DEFAULT_HHS: Haittojenhallintasuunnitelma =
        mapOf(
            Haittojenhallintatyyppi.YLEINEN to DEFAULT_HHS_YLEINEN,
            Haittojenhallintatyyppi.PYORALIIKENNE to DEFAULT_HHS_PYORALIIKENNE,
            Haittojenhallintatyyppi.AUTOLIIKENNE to DEFAULT_HHS_AUTOLIIKENNE,
            Haittojenhallintatyyppi.LINJAAUTOLIIKENNE to DEFAULT_HHS_LINJAAUTOLIIKENNE,
            Haittojenhallintatyyppi.RAITIOLIIKENNE to DEFAULT_HHS_RAITIOLIIKENNE,
            Haittojenhallintatyyppi.MUUT to DEFAULT_HHS_MUUT,
        )

    fun create(
        id: Int? = 1,
        hankeId: Int? = 2,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = GeometriaFactory.create(),
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? =
            VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? =
            AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA,
        meluHaitta: Meluhaitta? = Meluhaitta.SATUNNAINEN_MELUHAITTA,
        polyHaitta: Polyhaitta? = Polyhaitta.TOISTUVA_POLYHAITTA,
        tarinaHaitta: Tarinahaitta? = Tarinahaitta.JATKUVA_TARINAHAITTA,
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
        tormaystarkasteluTulos: TormaystarkasteluTulos? = tormaystarkasteluTulos(),
        haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma? =
            createHaittojenhallintasuunnitelma(),
    ): SavedHankealue {
        return SavedHankealue(
            id,
            hankeId,
            haittaAlkuPvm,
            haittaLoppuPvm,
            geometriat,
            kaistaHaitta,
            kaistaPituusHaitta,
            meluHaitta,
            polyHaitta,
            tarinaHaitta,
            nimi,
            tormaystarkasteluTulos,
            haittojenhallintasuunnitelma,
        )
    }

    /**
     * Creates a haittojenhallintasuunnitelma with values in all fields.
     *
     * Values can be overridden with parameters like
     * `createHaittojenhallintasuunnitelma(Haittojenhallintatyyppi.YLEINEN to "Overridden value")`.
     *
     * Values can be removed from the haittojenhallintasuunnitelma by overriding them with null:
     * `createHaittojenhallintasuunnitelma(Haittojenhallintatyyppi.YLEINEN to null)`.
     */
    fun createHaittojenhallintasuunnitelma(
        vararg overrides: Pair<Haittojenhallintatyyppi, String?>
    ): Haittojenhallintasuunnitelma {
        val haittojenhallintasuunnitelma = DEFAULT_HHS.toMutableMap()
        overrides.forEach { (haittojenhallintatyyppi, suunnitelma) ->
            if (suunnitelma == null) {
                haittojenhallintasuunnitelma.remove(haittojenhallintatyyppi)
            } else {
                haittojenhallintasuunnitelma[haittojenhallintatyyppi] = suunnitelma
            }
        }
        return haittojenhallintasuunnitelma
    }

    fun createMinimal(
        id: Int? = null,
        hankeId: Int? = null,
        haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        geometriat: Geometriat? = null,
        kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
        kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
        meluHaitta: Meluhaitta? = null,
        polyHaitta: Polyhaitta? = null,
        tarinaHaitta: Tarinahaitta? = null,
        nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
    ): SavedHankealue {
        return SavedHankealue(
            id,
            hankeId,
            haittaAlkuPvm,
            haittaLoppuPvm,
            geometriat,
            kaistaHaitta,
            kaistaPituusHaitta,
            meluHaitta,
            polyHaitta,
            tarinaHaitta,
            nimi,
            null,
        )
    }

    fun createHankeAlueEntity(mockId: Int = 1, hankeEntity: HankeEntity): HankealueEntity {
        val alue = create(id = mockId).apply { geometriat?.id = mockId }
        return HankealueEntity(
                id = alue.id!!,
                hanke = hankeEntity,
                geometriat = alue.geometriat?.id,
                haittaAlkuPvm = DateFactory.getStartDatetime().toLocalDate(),
                haittaLoppuPvm = DateFactory.getEndDatetime().toLocalDate(),
                kaistaHaitta = alue.kaistaHaitta,
                kaistaPituusHaitta = alue.kaistaPituusHaitta,
                meluHaitta = alue.meluHaitta,
                polyHaitta = alue.polyHaitta,
                tarinaHaitta = alue.tarinaHaitta,
                nimi = alue.nimi,
                tormaystarkasteluTulos = null,
            )
            .apply {
                tormaystarkasteluTulos = tormaystarkasteluTulosEntity(hankealueEntity = this)
                alue.haittojenhallintasuunnitelma?.let { haittojenhallintasuunnitelma.putAll(it) }
            }
    }

    private fun tormaystarkasteluTulos() =
        TormaystarkasteluTulos(
            autoliikenne = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU,
            pyoraliikenneindeksi = 2.5f,
            linjaautoliikenneindeksi = 3.75f,
            raitioliikenneindeksi = 3.75f,
        )

    private fun tormaystarkasteluTulosEntity(
        id: Int = 1,
        hankealueEntity: HankealueEntity,
    ): TormaystarkasteluTulosEntity =
        TormaystarkasteluTulosEntity(
            id = id,
            autoliikenne = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU.indeksi,
            haitanKesto = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU.haitanKesto,
            katuluokka = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU.katuluokka,
            autoliikennemaara = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU.liikennemaara,
            kaistahaitta = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU.kaistahaitta,
            kaistapituushaitta =
                TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU.kaistapituushaitta,
            pyoraliikenne = 2.5f,
            linjaautoliikenne = 3.75f,
            raitioliikenne = 3.75f,
            hankealue = hankealueEntity,
        )
}
