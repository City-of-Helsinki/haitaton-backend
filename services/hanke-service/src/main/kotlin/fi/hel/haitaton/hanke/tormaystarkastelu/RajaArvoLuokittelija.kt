package fi.hel.haitaton.hanke.tormaystarkastelu

object RajaArvoLuokittelija {

    fun getHaittaAjanKestoLuokka(days: Int): Int =
        when {
            days >= 91 -> HaittaAjanKestoLuokittelu.YLI_3KK.value
            days >= 14 -> HaittaAjanKestoLuokittelu.ALLE_3KK_YLI_2VK.value
            days >= 0 -> HaittaAjanKestoLuokittelu.ALLE_2VK.value
            else -> 0
        }

    fun getLiikennemaaraLuokka(volume: Int) =
        when {
            volume >= 10000 -> LiikenneMaaraLuokittelu.YLI_10K
            volume >= 5000 -> LiikenneMaaraLuokittelu._5K_10K
            volume >= 1500 -> LiikenneMaaraLuokittelu._1500_5K
            volume >= 500 -> LiikenneMaaraLuokittelu._500_1500
            volume >= 1 -> LiikenneMaaraLuokittelu.ALLE_500
            else -> LiikenneMaaraLuokittelu.EI_LIIKENNETTA
        }.value

    fun getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses: Int) =
        when {
            countOfRushHourBuses >= 21 -> BussiLiikenneLuokittelu.KAMPPI_RAUTATIENTORI
            countOfRushHourBuses >= 11 -> BussiLiikenneLuokittelu.RUNKOLINJA
            countOfRushHourBuses >= 5 -> BussiLiikenneLuokittelu.RUNKOLINJAMAINEN
            countOfRushHourBuses >= 0 -> BussiLiikenneLuokittelu.PERUS
            else -> BussiLiikenneLuokittelu.EI_VAIKUTA
        }.value
}
