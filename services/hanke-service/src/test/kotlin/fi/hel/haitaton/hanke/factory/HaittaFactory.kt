package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.tormaystarkastelu.Autoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.HaittaAjanKestoLuokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Liikennemaaraluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluKatuluokka
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin

object HaittaFactory {

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

    fun tormaystarkasteluTulos(
        autoliikenne: Autoliikenneluokittelu = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU,
        pyoraliikenneindeksi: Float = 2.5f,
        linjaautoliikenneindeksi: Float = 3.75f,
        raitioliikenneindeksi: Float = 3.75f,
    ) =
        TormaystarkasteluTulos(
            autoliikenne,
            pyoraliikenneindeksi,
            linjaautoliikenneindeksi,
            raitioliikenneindeksi,
        )
}
