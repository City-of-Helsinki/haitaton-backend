package fi.hel.haitaton.hanke.tormaystarkastelu

object RajaArvoLuokittelija {

    fun haittaajankestoluokka(days: Int): Int =
        when {
            days >= 91 -> HaittaAjanKestoLuokittelu.YLI_KOLME_KUUKAUTTA.value
            days >= 14 -> HaittaAjanKestoLuokittelu.KAKSI_VIIKKOA_VIIVA_KOLME_KUUKAUTTA.value
            days >= 0 -> HaittaAjanKestoLuokittelu.ALLE_KAKSI_VIIKKOA.value
            else -> 0
        }

    fun liikennemaaraluokka(volume: Int) =
        when {
            volume >= 10000 -> Liikennemaaraluokittelu.LIIKENNEMAARA_10000_TAI_ENEMMAN
            volume >= 5000 -> Liikennemaaraluokittelu.LIIKENNEMAARA_5000_9999
            volume >= 1500 -> Liikennemaaraluokittelu.LIIKENNEMAARA_1500_4999
            volume >= 500 -> Liikennemaaraluokittelu.LIIKENNEMAARA_500_1499
            volume >= 1 -> Liikennemaaraluokittelu.LIIKENNEMAARA_ALLE_500
            else -> Liikennemaaraluokittelu.EI_LIIKENNETTA
        }.value

    fun linjaautoliikenteenRuuhkavuoroluokka(countOfRushHourBuses: Int) =
        when {
            countOfRushHourBuses > 20 -> Linjaautoliikenneluokittelu.TARKEIMMAT_JOUKKOLIIKENNEKADUT
            countOfRushHourBuses > 10 -> Linjaautoliikenneluokittelu.RUNKOLINJA
            countOfRushHourBuses > 0 -> Linjaautoliikenneluokittelu.VUOROJA_RUUHKAAIKANA
            countOfRushHourBuses == 0 -> Linjaautoliikenneluokittelu.EI_VUOROJA_RUUHKAAIKANA
            else -> Linjaautoliikenneluokittelu.EI_VAIKUTA_LINJAAUTOLIIKENTEESEEN
        }.value
}
