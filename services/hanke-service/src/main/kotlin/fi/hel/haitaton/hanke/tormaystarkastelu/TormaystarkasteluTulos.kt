package fi.hel.haitaton.hanke.tormaystarkastelu

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.HankeEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kotlin.math.max

@Schema(description = "Collision review result")
data class TormaystarkasteluTulos(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Basic index result")
    val perusIndeksi: Float,
    //
    @field:Schema(description = "Cycling index result")
    @JsonView(ChangeLogView::class)
    val pyorailyIndeksi: Float,
    //
    @field:Schema(description = "Bus transport index result")
    @JsonView(ChangeLogView::class)
    val linjaautoIndeksi: Float,
    //
    @field:Schema(description = "Tram transport index result")
    @JsonView(ChangeLogView::class)
    val raitiovaunuIndeksi: Float,
) {
    @get:JsonView(ChangeLogView::class)
    @delegate:Schema(
        description =
            "Public transport index result, worst of linjaautoIndeksi and raitiovaunuIndeksi"
    )
    val joukkoliikenneIndeksi: Float by lazy { max(linjaautoIndeksi, raitiovaunuIndeksi) }

    @get:JsonView(ChangeLogView::class)
    val liikennehaittaIndeksi: LiikennehaittaIndeksiType by lazy {
        val max = maxOf(perusIndeksi, pyorailyIndeksi, linjaautoIndeksi, raitiovaunuIndeksi)
        val type =
            when (max) {
                linjaautoIndeksi -> IndeksiType.LINJAAUTOINDEKSI
                raitiovaunuIndeksi -> IndeksiType.RAITIOVAUNUINDEKSI
                perusIndeksi -> IndeksiType.PERUSINDEKSI
                else -> IndeksiType.PYORAILYINDEKSI
            }
        LiikennehaittaIndeksiType(max, type)
    }
}

@Schema(description = "Traffic nuisance index type")
data class LiikennehaittaIndeksiType(
    @JsonView(ChangeLogView::class) val indeksi: Float,
    @JsonView(ChangeLogView::class) val tyyppi: IndeksiType
)

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    LINJAAUTOINDEKSI,
    RAITIOVAUNUINDEKSI,
}

@Entity
@Table(name = "tormaystarkastelutulos")
class TormaystarkasteluTulosEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    val perus: Float,
    val pyoraily: Float,
    val linjaauto: Float,
    val raitiovaunu: Float,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    val hanke: HankeEntity,
)
