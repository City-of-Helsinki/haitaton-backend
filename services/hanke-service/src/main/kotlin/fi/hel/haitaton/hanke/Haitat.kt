package fi.hel.haitaton.hanke

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.HaittaAjanKestoLuokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Liikennemaaraluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Linjaautoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Pyoraliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Raitioliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluKatuluokka
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin

data class Haitat(
    val pyoraliikenne: HaittaPyoraliikenteelle,
    val autoliikenne: HaittaAutoliikenteelle,
    val linjaautojenPaikallisliikenne: HaittaLinjaautojenPaikallisliikenteelle,
    val raitioliikenne: HaittaRaitioliikenteelle,
    val muu: MuuHaitta,
)

enum class Haittatyyppi {
    PYORALIIKENNE,
    AUTOLIIKENNE,
    LINJA_AUTOJEN_PAIKALLISLIIKENNE,
    RAITIOLIIKENNE,
    MUU,
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "tyyppi",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = HaittaPyoraliikenteelle::class, name = "PYORALIIKENNE"),
    JsonSubTypes.Type(value = HaittaAutoliikenteelle::class, name = "AUTOLIIKENNE"),
    JsonSubTypes.Type(
        value = HaittaLinjaautojenPaikallisliikenteelle::class,
        name = "LINJA_AUTOJEN_PAIKALLISLIIKENNE"
    ),
    JsonSubTypes.Type(value = HaittaRaitioliikenteelle::class, name = "RAITIOLIIKENNE"),
    JsonSubTypes.Type(value = MuuHaitta::class, name = "MUU"),
)
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface Haitta {
    val tyyppi: Haittatyyppi // needed for json deserialization
    val hallintasuunnitelma: String
}

sealed interface Liikennehaitta : Haitta {
    val indeksi: Float
}

data class HaittaPyoraliikenteelle(
    val luokka: Pyoraliikenneluokittelu,
    override val hallintasuunnitelma: String,
) : Liikennehaitta {
    override val tyyppi: Haittatyyppi
        get() = Haittatyyppi.PYORALIIKENNE

    override val indeksi: Float by lazy { if (luokka.value >= 4) 3.0f else 1.0f }
}

data class HaittaAutoliikenteelle(
    val kesto: HaittaAjanKestoLuokittelu,
    val katuluokka: TormaystarkasteluKatuluokka,
    val liikennemaara: Liikennemaaraluokittelu,
    val kaistamaara: VaikutusAutoliikenteenKaistamaariin,
    val kaistahaittojenPituus: AutoliikenteenKaistavaikutustenPituus,
    override val hallintasuunnitelma: String,
) : Liikennehaitta {
    override val tyyppi: Haittatyyppi
        get() = Haittatyyppi.AUTOLIIKENNE

    override val indeksi: Float by lazy {
        (kesto.value * 0.1f +
                katuluokka.value * 0.2f +
                liikennemaara.value * 0.25f +
                kaistamaara.value * 0.25f +
                kaistahaittojenPituus.value * 0.2f)
            .roundToOneDecimal()
    }
}

data class HaittaLinjaautojenPaikallisliikenteelle(
    val luokka: Linjaautoliikenneluokittelu,
    override val hallintasuunnitelma: String,
) : Liikennehaitta {
    override val tyyppi: Haittatyyppi
        get() = Haittatyyppi.LINJA_AUTOJEN_PAIKALLISLIIKENNE

    override val indeksi: Float by lazy { luokka.value.toFloat() }
}

data class HaittaRaitioliikenteelle(
    val luokka: Raitioliikenneluokittelu,
    override val hallintasuunnitelma: String,
) : Liikennehaitta {
    override val tyyppi: Haittatyyppi
        get() = Haittatyyppi.RAITIOLIIKENNE

    override val indeksi: Float by lazy { luokka.value.toFloat() }
}

data class MuuHaitta(
    val melu: Meluhaitta,
    val poly: Polyhaitta,
    val tarina: Tarinahaitta,
    override val hallintasuunnitelma: String,
) : Haitta {
    override val tyyppi: Haittatyyppi
        get() = Haittatyyppi.MUU
}
