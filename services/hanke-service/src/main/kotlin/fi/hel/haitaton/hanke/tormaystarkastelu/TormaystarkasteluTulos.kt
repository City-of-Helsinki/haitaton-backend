package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeEntity
import javax.persistence.*

data class TormaystarkasteluTulos(
    val perusIndeksi: Float,
    val pyorailyIndeksi: Float,
    val joukkoliikenneIndeksi: Float
) {

    val liikennehaittaIndeksi: LiikennehaittaIndeksiType by lazy {
        val maxIndex =
            setOf(perusIndeksi, pyorailyIndeksi, joukkoliikenneIndeksi).maxOrNull()
                ?: throw IllegalStateException(
                    "perusIndeksi, pyorailyIndeksi and joukkoliikenneIndeksi cannot be null"
                )
        when (maxIndex) {
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
class TormaystarkasteluTulosEntity(
    val perus: Float,
    val pyoraily: Float,
    val joukkoliikenne: Float,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeid")
    val hanke: HankeEntity,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TormaystarkasteluTulosEntity

        if (perus != other.perus) return false
        if (pyoraily != other.pyoraily) return false
        if (joukkoliikenne != other.joukkoliikenne) return false
        if (hanke != other.hanke) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = perus.hashCode()
        result = 31 * result + pyoraily.hashCode()
        result = 31 * result + joukkoliikenne.hashCode()
        result = 31 * result + hanke.hashCode()
        result = 31 * result + id
        return result
    }
}
