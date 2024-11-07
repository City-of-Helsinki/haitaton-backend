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
        VaikutusAutoliikenteenKaistamaariin.EI_VAIKUTA -> "Ei vaikuta autoliikenteen kaistamääriin"
        VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE ->
            "Yksi autokaista vähenee - ajosuunta vielä käytössä"
        VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA ->
            "Yksi autokaista vähenee kahdella ajosuunnalla - ajosuunnat vielä käytössä"
        VaikutusAutoliikenteenKaistamaariin.USEITA_KAISTOJA_VAHENEE_AJOSUUNNILLA ->
            "Useita autokaistoja vähenee ajosuunnilla - ajosuunnat vielä käytössä"
        VaikutusAutoliikenteenKaistamaariin.YKSI_AJOSUUNTA_POISTUU_KAYTOSTA ->
            "Yksi autoliikenteen ajosuunta poistuu käytöstä"
        VaikutusAutoliikenteenKaistamaariin.USEITA_AJOSUUNTIA_POISTUU_KAYTOSTA ->
            "Useita autoliikenteen ajosuuntia poistuu käytöstä"
    }

fun AutoliikenteenKaistavaikutustenPituus.format(): String =
    when (this) {
        AutoliikenteenKaistavaikutustenPituus.EI_VAIKUTA_KAISTAJARJESTELYIHIN ->
            "Ei vaikuta autoliikenteen kaistajärjestelyihin"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA ->
            "Kaistavaikutusten pituus alle 10 m"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA ->
            "Kaistavaikutusten pituus 10-99 m"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_100_499_METRIA ->
            "Kaistavaikutusten pituus 100-499 m"
        AutoliikenteenKaistavaikutustenPituus.PITUUS_500_METRIA_TAI_ENEMMAN ->
            "Kaistavaikutusten pituus 500 m tai enemmän"
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
