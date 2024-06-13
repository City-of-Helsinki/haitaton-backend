package fi.hel.haitaton.hanke.tormaystarkastelu

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.roundToOneDecimal
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Schema(description = "Collision review result")
@JsonIgnoreProperties(ignoreUnknown = true)
data class TormaystarkasteluTulos(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Car traffic index results")
    val autoliikenne: Autoliikenneluokittelu,
    //
    @field:Schema(description = "Cycling index result")
    @JsonView(ChangeLogView::class)
    val pyoraliikenneindeksi: Float,
    //
    @field:Schema(description = "Local bus traffic index result")
    @JsonView(ChangeLogView::class)
    val linjaautoliikenneindeksi: Float,
    //
    @field:Schema(description = "Tram traffic index result")
    @JsonView(ChangeLogView::class)
    val raitioliikenneindeksi: Float,
) {
    @get:JsonView(ChangeLogView::class)
    val liikennehaittaindeksi: LiikennehaittaindeksiType by lazy {
        val max =
            maxOf(
                autoliikenne.indeksi,
                pyoraliikenneindeksi,
                linjaautoliikenneindeksi,
                raitioliikenneindeksi
            )
        val type =
            when (max) {
                linjaautoliikenneindeksi -> IndeksiType.LINJAAUTOLIIKENNEINDEKSI
                raitioliikenneindeksi -> IndeksiType.RAITIOLIIKENNEINDEKSI
                autoliikenne.indeksi -> IndeksiType.AUTOLIIKENNEINDEKSI
                else -> IndeksiType.PYORALIIKENNEINDEKSI
            }
        LiikennehaittaindeksiType(max, type)
    }
}

@Schema(description = "Car traffic nuisance index and classification")
@JsonIgnoreProperties(ignoreUnknown = true)
data class Autoliikenneluokittelu(
    @JsonView(ChangeLogView::class) val indeksi: Float,
    @JsonView(ChangeLogView::class) val haitanKesto: Int,
    @JsonView(ChangeLogView::class) val katuluokka: Int,
    @JsonView(ChangeLogView::class) val liikennemaara: Int,
    @JsonView(ChangeLogView::class) val kaistahaitta: Int,
    @JsonView(ChangeLogView::class) val kaistapituushaitta: Int,
) {
    companion object {
        fun calculateIndeksi(
            haitanKesto: Int,
            katuluokka: Int,
            liikennemaara: Int,
            kaistahaitta: Int,
            kaistapituushaitta: Int,
        ): Float =
            if (katuluokka == 0 && liikennemaara == 0) {
                0.0f
            } else {
                (0.1f * haitanKesto +
                        0.2f * katuluokka +
                        0.25f * liikennemaara +
                        0.25f * kaistahaitta +
                        0.2f * kaistapituushaitta)
                    .roundToOneDecimal()
            }
    }

    constructor(
        haitanKesto: Int,
        katuluokka: Int,
        liikennemaara: Int,
        kaistahaitta: Int,
        kaistapituushaitta: Int,
    ) : this(
        calculateIndeksi(haitanKesto, katuluokka, liikennemaara, kaistahaitta, kaistapituushaitta),
        haitanKesto,
        katuluokka,
        liikennemaara,
        kaistahaitta,
        kaistapituushaitta,
    )
}

@Schema(description = "Traffic nuisance index type")
data class LiikennehaittaindeksiType(
    @JsonView(ChangeLogView::class) val indeksi: Float,
    @JsonView(ChangeLogView::class) val tyyppi: IndeksiType
)

enum class IndeksiType {
    AUTOLIIKENNEINDEKSI,
    PYORALIIKENNEINDEKSI,
    LINJAAUTOLIIKENNEINDEKSI,
    RAITIOLIIKENNEINDEKSI,
}

@Entity
@Table(name = "tormaystarkastelutulos")
class TormaystarkasteluTulosEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    val autoliikenne: Float,
    @Column(name = "haitan_kesto") val haitanKesto: Int,
    val katuluokka: Int,
    val autoliikennemaara: Int,
    val kaistahaitta: Int,
    val kaistapituushaitta: Int,
    val pyoraliikenne: Float,
    val linjaautoliikenne: Float,
    val raitioliikenne: Float,
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankealue_id")
    val hankealue: HankealueEntity,
) {
    fun toDomain(): TormaystarkasteluTulos =
        TormaystarkasteluTulos(
            Autoliikenneluokittelu(
                autoliikenne,
                haitanKesto,
                katuluokka,
                autoliikennemaara,
                kaistahaitta,
                kaistapituushaitta,
            ),
            pyoraliikenne,
            linjaautoliikenne,
            raitioliikenne,
        )
}
