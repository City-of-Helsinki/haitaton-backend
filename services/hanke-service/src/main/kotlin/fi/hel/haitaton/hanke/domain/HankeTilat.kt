package fi.hel.haitaton.hanke.domain

// Note: not a data class because that would require adding at least one constructor parameter.
class HankeTilat {
    // NOTE: Flags are handled internally, and are not saved directly to database.
    // Instead, in order to change flag(s), one needs to either set the value(s) and call
    // updateHankeStateFlags(hanke: Hanke), or call relevant updateStateFlag..() function
    // in the Hanke domain instance.
    // TODO: englanniksi?
    var onGeometrioita: Boolean = false // Use updateHankeStateFlags()
    var onKaikkiPakollisetLuontiTiedot: Boolean = false // Not saved to databased
    var onTiedotLiikHaittaIndeksille: Boolean = false // Not saved to databased
    var onLiikHaittaIndeksi: Boolean = false // Not saved to databased
    var onViereisiaHankkeita: Boolean = false // Use updateHankeStateFlags()
    var onAsiakasryhmia: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HankeTilat) return false

        if (onGeometrioita != other.onGeometrioita) return false
        if (onKaikkiPakollisetLuontiTiedot != other.onKaikkiPakollisetLuontiTiedot) return false
        if (onTiedotLiikHaittaIndeksille != other.onTiedotLiikHaittaIndeksille) return false
        if (onLiikHaittaIndeksi != other.onLiikHaittaIndeksi) return false
        if (onViereisiaHankkeita != other.onViereisiaHankkeita) return false
        if (onAsiakasryhmia != other.onAsiakasryhmia) return false

        return true
    }

    override fun hashCode(): Int {
        // Crude, but likely never used for realz
        var result = (if (onGeometrioita) 1 else 0)
        result = 31 * result + (if (onKaikkiPakollisetLuontiTiedot) 1 else 0)
        result = 31 * result + (if (onTiedotLiikHaittaIndeksille) 1 else 0)
        result = 31 * result + (if (onLiikHaittaIndeksi) 1 else 0)
        result = 31 * result + (if (onViereisiaHankkeita) 1 else 0)
        result = 31 * result + (if (onAsiakasryhmia) 1 else 0)
        return result
    }

}
