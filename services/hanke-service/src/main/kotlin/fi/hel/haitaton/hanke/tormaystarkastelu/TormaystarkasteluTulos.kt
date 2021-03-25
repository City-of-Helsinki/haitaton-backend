package fi.hel.haitaton.hanke.tormaystarkastelu

import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EnumType
import javax.persistence.Enumerated

// TODO: Only hankeTunnus to be used as part of equals(), hashCode() and copy() auto-implementations?
//  For now we only have one result per hanke, so it is ok, but in future there can be multiple results
//  for each hanke, and at least geometryId or something like that should be considered.
data class TormaystarkasteluTulos(val hankeTunnus: String) {

    var hankeId: Int = 0
    var hankeGeometriatId: Int = 0
    var liikennehaittaIndeksi: LiikennehaittaIndeksiType? = null
    var perusIndeksi: Float? = null
    var pyorailyIndeksi: Float? = null
    var joukkoliikenneIndeksi: Float? = null
    var tila: TormaystarkasteluTulosTila? = null

    override fun toString(): String {
        return "TormaystarkasteluTulos(" +
                "hankeTunnus='$hankeTunnus', " +
                "hankeId=$hankeId, " +
                "hankeGeometriatId=$hankeGeometriatId, " +
                "liikennehaittaIndeksi=$liikennehaittaIndeksi, " +
                "perusIndeksi=$perusIndeksi, " +
                "pyorailyIndeksi=$pyorailyIndeksi, " +
                "joukkoliikenneIndeksi=$joukkoliikenneIndeksi)"
    }

    /**
     * Selects maximum of three indeksi and sets that to be LiikennehaittaIndeksi.
     */
    fun calculateLiikennehaittaIndeksi() {
        val maxIndex = setOf(perusIndeksi!!, pyorailyIndeksi!!, joukkoliikenneIndeksi!!).maxOrNull()
            ?: throw IllegalStateException("perusIndeksi, pyorailyIndeksi and joukkoliikenneIndeksi cannot be null")
        liikennehaittaIndeksi = when (maxIndex) {
            joukkoliikenneIndeksi -> LiikennehaittaIndeksiType(
                joukkoliikenneIndeksi!!,
                IndeksiType.JOUKKOLIIKENNEINDEKSI
            )
            perusIndeksi -> LiikennehaittaIndeksiType(perusIndeksi!!, IndeksiType.PERUSINDEKSI)
            else -> LiikennehaittaIndeksiType(pyorailyIndeksi!!, IndeksiType.PYORAILYINDEKSI)
        }
    }
}

// This class is also used as part of entity-classes TormaystarkasteluTulosEntity and Hanke.
// HankeEntity overrides the column names.
@Embeddable
data class LiikennehaittaIndeksiType(
    @Column(name = "liikennehaitta")
    var indeksi: Float,
    @Column(name = "liikennehaittatyyppi")
    @Enumerated(EnumType.STRING)
    var tyyppi: IndeksiType
)

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    JOUKKOLIIKENNEINDEKSI
    // TODO: will likely need a new option "MONTA" or "USEITA".
}

enum class TormaystarkasteluTulosTila {
    VOIMASSA,
    EI_VOIMASSA
}
