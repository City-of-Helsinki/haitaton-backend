package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeEntity
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

data class TormaystarkasteluTulos(
    val perusIndeksi: Float,
    val pyorailyIndeksi: Float,
    val joukkoliikenneIndeksi: Float
) {

    val liikennehaittaIndeksi: LiikennehaittaIndeksiType by lazy {
        when (maxOf(perusIndeksi, pyorailyIndeksi, joukkoliikenneIndeksi)) {
            joukkoliikenneIndeksi ->
                LiikennehaittaIndeksiType(joukkoliikenneIndeksi, IndeksiType.JOUKKOLIIKENNEINDEKSI)
            perusIndeksi -> LiikennehaittaIndeksiType(perusIndeksi, IndeksiType.PERUSINDEKSI)
            else -> LiikennehaittaIndeksiType(pyorailyIndeksi, IndeksiType.PYORAILYINDEKSI)
        }
    }
}

data class LiikennehaittaIndeksiType(val indeksi: Float, val tyyppi: IndeksiType)

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    JOUKKOLIIKENNEINDEKSI
}

@Entity
@Table(name = "tormaystarkastelutulos")
data class TormaystarkasteluTulosEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    val perus: Float,
    val pyoraily: Float,
    val joukkoliikenne: Float,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    val hanke: HankeEntity,
)
