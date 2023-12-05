package fi.hel.haitaton.hanke.tormaystarkastelu

object RajaArvoLuokittelija {

    fun haittaajankestoluokka(days: Int): Int =
        when {
            days >= 91 -> HaittaAjanKestoLuokittelu.YLI_3KK.value
            days >= 14 -> HaittaAjanKestoLuokittelu.ALLE_3KK_YLI_2VK.value
            days >= 0 -> HaittaAjanKestoLuokittelu.ALLE_2VK.value
            else -> 0
        }

    fun liikennemaaraluokka(volume: Int) =
        when {
            volume >= 10000 -> Liikennemaaraluokittelu.YLI_10K
            volume >= 5000 -> Liikennemaaraluokittelu._5K_10K
            volume >= 1500 -> Liikennemaaraluokittelu._1500_5K
            volume >= 500 -> Liikennemaaraluokittelu._500_1500
            volume >= 1 -> Liikennemaaraluokittelu.ALLE_500
            else -> Liikennemaaraluokittelu.EI_LIIKENNETTA
        }.value

    fun linjaautoliikenteenRuuhkavuoroluokka(countOfRushHourBuses: Int) =
        when {
            countOfRushHourBuses >= 21 -> Linjaautoliikenneluokittelu.KAMPPI_RAUTATIENTORI
            countOfRushHourBuses >= 11 -> Linjaautoliikenneluokittelu.RUNKOLINJA
            countOfRushHourBuses >= 5 -> Linjaautoliikenneluokittelu.RUNKOLINJAMAINEN
            countOfRushHourBuses >= 0 -> Linjaautoliikenneluokittelu.PERUS
            else -> Linjaautoliikenneluokittelu.EI_VAIKUTA
        }.value
}
