package fi.hel.haitaton.hanke.tormaystarkastelu

data class RajaArvo(val luokittelu: Luokittelu, val minimumValue: Int)

interface LuokitteluRajaArvotService {

    fun getHaittaAjanKestoLuokka(days: Int): Int
    fun getLiikennemaaraLuokka(volume: Int): Int
    fun getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses: Int): Int

}

class LuokitteluRajaArvotServiceHardCoded : LuokitteluRajaArvotService {

    private val rajaArvotHaittaAjanKesto = listOf(
            RajaArvo(HaittaAjanKestoLuokittelu.YLI_3KK, 91),
            RajaArvo(HaittaAjanKestoLuokittelu.ALLE_3KK_YLI_2VK, 14),
            RajaArvo(HaittaAjanKestoLuokittelu.ALLE_2VK, 0)
    )

    private val rajaArvotLiikennemaara = listOf(
            RajaArvo(LiikenneMaaraLuokittelu.YLI_10K,10000),
            RajaArvo(LiikenneMaaraLuokittelu._5K_10K, 5000),
            RajaArvo(LiikenneMaaraLuokittelu._1500_5K, 1500),
            RajaArvo(LiikenneMaaraLuokittelu._500_1500, 500),
            RajaArvo(LiikenneMaaraLuokittelu.ALLE_500, 1),
            RajaArvo(LiikenneMaaraLuokittelu.EI_LIIKENNETTA, 0)
    )

    private val rajaArvotBussi = listOf(
            RajaArvo(BussiLiikenneLuokittelu.KAMPPI_RAUTATIENTORI, 21),
            RajaArvo(BussiLiikenneLuokittelu.RUNKOLINJA, 11),
            RajaArvo(BussiLiikenneLuokittelu.RUNKOLINJAMAINEN, 5),
            RajaArvo(BussiLiikenneLuokittelu.PERUS, 0),
            RajaArvo(BussiLiikenneLuokittelu.EI_VAIKUTA, -1)
    )

    override fun getHaittaAjanKestoLuokka(days: Int) =
            rajaArvotHaittaAjanKesto.find { it.minimumValue <= days }?.luokittelu?.value ?: 0

    override fun getLiikennemaaraLuokka(volume: Int) =
            rajaArvotLiikennemaara.find { it.minimumValue <= volume }?.luokittelu?.value ?: 0

    override fun getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses: Int) =
            rajaArvotBussi.find { it.minimumValue <= countOfRushHourBuses }?.luokittelu?.value ?: 0

}
