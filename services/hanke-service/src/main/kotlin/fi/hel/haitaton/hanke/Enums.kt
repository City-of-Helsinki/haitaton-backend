package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.tormaystarkastelu.Luokittelu

enum class HankeStatus {
    /** A hanke is a draft from its creation until all mandatory fields have been filled. */
    DRAFT,
    /**
     * A hanke goes public after all mandatory fields have been filled. This happens automatically
     * on any update. A public hanke has some info visible to everyone and applications can be added
     * to it.
     */
    PUBLIC,
    /**
     * After the end dates of all hankealue have passed, a hanke is considered finished. It's
     * anonymized and at least mostly hidden in the UI.
     */
    ENDED,
}

enum class Vaihe {
    OHJELMOINTI,
    SUUNNITTELU,
    RAKENTAMINEN
}

enum class TyomaaTyyppi {
    VESI,
    VIEMARI,
    SADEVESI,
    SAHKO,
    TIETOLIIKENNE,
    LIIKENNEVALO,
    ULKOVALAISTUS,
    KAAPPITYO,
    KAUKOLAMPO,
    KAUKOKYLMA,
    KAASUJOHTO,
    KISKOTYO,
    MUU,
    KADUNRAKENNUS,
    KADUN_KUNNOSSAPITO,
    KIINTEISTOLIITTYMA,
    SULKU_TAI_KAIVO,
    UUDISRAKENNUS,
    SANEERAUS,
    AKILLINEN_VIKAKORJAUS,
    VIHERTYO,
    RUNKOLINJA,
    NOSTOTYO,
    MUUTTO,
    PYSAKKITYO,
    KIINTEISTOREMONTTI,
    ULKOMAINOS,
    KUVAUKSET,
    LUMENPUDOTUS,
    YLEISOTILAISUUS,
    VAIHTOLAVA
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class VaikutusAutoliikenteenKaistamaariin(override val value: Int) : Luokittelu {
    EI_VAIKUTA(1),
    VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA(2),
    VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA(3),
    VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA(4),
    VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA(5)
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class AutoliikenteenKaistavaikutustenPituus(override val value: Int) : Luokittelu {
    EI_VAIKUTA_KAISTAJARJESTELYIHIN(1),
    KAISTAVAIKUTUSTEN_PITUUS_ALLE_10_METRIA(2),
    KAISTAVAIKUTUSTEN_PITUUS_10_99_METRIA(3),
    KAISTAVAIKUTUSTEN_PITUUS_100_499_METRIA(4),
    KAISTAVAIKUTUSTEN_PITUUS_500_METRIA_TAI_ENEMMAN(5)
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Meluhaitta {
    SATUNNAINEN_HAITTA,
    LYHYTAIKAINEN_TOISTUVA_HAITTA,
    PITKAKESTOINEN_TOISTUVA_HAITTA
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Polyhaitta {
    SATUNNAINEN_HAITTA,
    LYHYTAIKAINEN_TOISTUVA_HAITTA,
    PITKAKESTOINEN_TOISTUVA_HAITTA
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Tarinahaitta {
    SATUNNAINEN_HAITTA,
    LYHYTAIKAINEN_TOISTUVA_HAITTA,
    PITKAKESTOINEN_TOISTUVA_HAITTA
}
