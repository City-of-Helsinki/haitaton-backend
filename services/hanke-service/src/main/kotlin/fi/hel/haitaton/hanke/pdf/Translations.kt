package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin

fun Meluhaitta.format(): String =
    when (this) {
        Meluhaitta.JATKUVA_MELUHAITTA -> "Jatkuva meluhaitta"
        Meluhaitta.TOISTUVA_MELUHAITTA -> "Toistuva meluhaitta"
        Meluhaitta.SATUNNAINEN_MELUHAITTA -> "Satunnainen meluhaitta"
        Meluhaitta.EI_MELUHAITTAA -> "Ei meluhaittaa"
    }

fun Polyhaitta.format(): String =
    when (this) {
        Polyhaitta.JATKUVA_POLYHAITTA -> "Jatkuva pölyhaitta"
        Polyhaitta.TOISTUVA_POLYHAITTA -> "Toistuva pölyhaitta"
        Polyhaitta.SATUNNAINEN_POLYHAITTA -> "Satunnainen pölyhaitta"
        Polyhaitta.EI_POLYHAITTAA -> "Ei pölyhaittaa"
    }

fun Tarinahaitta.format(): String =
    when (this) {
        Tarinahaitta.JATKUVA_TARINAHAITTA -> "Jatkuva tärinähaitta"
        Tarinahaitta.TOISTUVA_TARINAHAITTA -> "Toistuva tärinähaitta"
        Tarinahaitta.SATUNNAINEN_TARINAHAITTA -> "Satunnainen tärinähaitta"
        Tarinahaitta.EI_TARINAHAITTAA -> "Ei tärinähaittaa"
    }

fun VaikutusAutoliikenteenKaistamaariin.format(): String =
    when (this) {
        VaikutusAutoliikenteenKaistamaariin.EI_VAIKUTA -> "Ei vaikuta"
        VaikutusAutoliikenteenKaistamaariin.VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA ->
            "Vähentää kaistan yhdellä ajosuunnalla"

        VaikutusAutoliikenteenKaistamaariin
            .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA ->
            "Vähentää samanaikaisesti kaistan kahdella ajosuunnalla"

        VaikutusAutoliikenteenKaistamaariin
            .VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA ->
            "Vähentää samanaikaisesti useita kaistoja kahdella ajosuunnalla"

        VaikutusAutoliikenteenKaistamaariin
            .VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA ->
            "Vähentää samanaikaisesti useita kaistoja liittymän eri suunnilla"
    }

fun AutoliikenteenKaistavaikutustenPituus.format(): String =
    when (this) {
        AutoliikenteenKaistavaikutustenPituus.EI_VAIKUTA_KAISTAJARJESTELYIHIN -> "Ei vaikuta"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA -> "Alle 10 m"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA -> "10-99 m"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_100_499_METRIA -> "100-499 m"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_500_METRIA_TAI_ENEMMAN -> "500 m tai enemmän"
    }

fun TyomaaTyyppi.format(): String =
    when (this) {
        TyomaaTyyppi.VESI -> "Vesi"
        TyomaaTyyppi.VIEMARI -> "Viemäri"
        TyomaaTyyppi.SADEVESI -> "Sadevesi"
        TyomaaTyyppi.SAHKO -> "Sähkö"
        TyomaaTyyppi.TIETOLIIKENNE -> "Tietoliikenne"
        TyomaaTyyppi.LIIKENNEVALO -> "Liikennevalo"
        TyomaaTyyppi.ULKOVALAISTUS -> "Ulkovalaistus"
        TyomaaTyyppi.KAAPPITYO -> "Kaappityö"
        TyomaaTyyppi.KAUKOLAMPO -> "Kaukolämpö"
        TyomaaTyyppi.KAUKOKYLMA -> "Kaukokylmä"
        TyomaaTyyppi.KAASUJOHTO -> "Kaasujohto"
        TyomaaTyyppi.KISKOTYO -> "Kiskotyö"
        TyomaaTyyppi.MUU -> "Muu"
        TyomaaTyyppi.KADUNRAKENNUS -> "Kadunrakennus"
        TyomaaTyyppi.KADUN_KUNNOSSAPITO -> "Kadun kunnossapito"
        TyomaaTyyppi.KIINTEISTOLIITTYMA -> "Kiinteistöliittymä"
        TyomaaTyyppi.SULKU_TAI_KAIVO -> "Sulku tai kaivo"
        TyomaaTyyppi.UUDISRAKENNUS -> "Uudisrakentaminen"
        TyomaaTyyppi.SANEERAUS -> "Saneeraus"
        TyomaaTyyppi.AKILLINEN_VIKAKORJAUS -> "Äkillinen vikakorjaus"
        TyomaaTyyppi.VIHERTYO -> "Vihertyö"
        TyomaaTyyppi.RUNKOLINJA -> "Runkolinja"
        TyomaaTyyppi.NOSTOTYO -> "Nostotyö"
        TyomaaTyyppi.MUUTTO -> "Muutto"
        TyomaaTyyppi.PYSAKKITYO -> "Pysäkkityö"
        TyomaaTyyppi.KIINTEISTOREMONTTI -> "Kiinteistöremontti"
        TyomaaTyyppi.ULKOMAINOS -> "Ulkomainos"
        TyomaaTyyppi.KUVAUKSET -> "Kuvaukset"
        TyomaaTyyppi.LUMENPUDOTUS -> "Lumenpudotus"
        TyomaaTyyppi.YLEISOTILAISUUS -> "Yleisötilaisuus"
        TyomaaTyyppi.VAIHTOLAVA -> "Vaihtolava"
    }
