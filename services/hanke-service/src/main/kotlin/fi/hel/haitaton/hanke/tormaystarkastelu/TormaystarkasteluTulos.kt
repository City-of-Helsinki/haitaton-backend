package fi.hel.haitaton.hanke.tormaystarkastelu

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

@Schema(description = "Collision review result")
@JsonIgnoreProperties(ignoreUnknown = true)
data class TormaystarkasteluTulos(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Car traffic index result")
    val autoliikenneindeksi: Float,
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
                autoliikenneindeksi,
                pyoraliikenneindeksi,
                linjaautoliikenneindeksi,
                raitioliikenneindeksi
            )
        val type =
            when (max) {
                linjaautoliikenneindeksi -> IndeksiType.LINJAAUTOLIIKENNEINDEKSI
                raitioliikenneindeksi -> IndeksiType.RAITIOLIIKENNEINDEKSI
                autoliikenneindeksi -> IndeksiType.AUTOLIIKENNEINDEKSI
                else -> IndeksiType.PYORALIIKENNEINDEKSI
            }
        LiikennehaittaindeksiType(max, type)
    }
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
    val pyoraliikenne: Float,
    val linjaautoliikenne: Float,
    val raitioliikenne: Float,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    val hanke: HankeEntity,
)
