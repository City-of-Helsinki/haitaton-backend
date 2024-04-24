package fi.hel.haitaton.hanke.domain

interface HankeRequest : HasYhteystiedot {
    val nimi: String
    val vaihe: Hankevaihe?
    val alueet: List<Hankealue>
    val tyomaaKatuosoite: String?
}
