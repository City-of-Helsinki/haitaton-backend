package fi.hel.haitaton.hanke.tormaystarkastelu

interface PerusIndeksiPainotService {

    fun getAll(): Map<LuokitteluType, Float>

}

class PerusIndeksiPainotServiceHardCoded : PerusIndeksiPainotService {

    private val perusindeksiPainot = mapOf(
            Pair(LuokitteluType.HAITTA_AJAN_KESTO, 0.1f),
            Pair(LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN, 0.25f),
            Pair(LuokitteluType.KAISTAJARJESTELYN_PITUUS, 0.2f),
            Pair(LuokitteluType.KATULUOKKA, 0.2f),
            Pair(LuokitteluType.LIIKENNEMAARA, 0.25f)
    )

    override fun getAll() = perusindeksiPainot

}