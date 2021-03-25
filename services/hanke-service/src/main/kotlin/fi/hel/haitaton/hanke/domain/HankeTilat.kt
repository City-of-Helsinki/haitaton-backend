package fi.hel.haitaton.hanke.domain

/**
 * NOTE: Flags are handled internally, and are not saved directly to database.
 * Instead, in order to change flag(s), one needs to either set the value(s) and call
 * HankeService.updateHankeStateFlags(hanke: Hanke),
 * or call relevant updateStateFlag..() function in the Hanke domain instance.
 */
data class HankeTilat(
    var onGeometrioita: Boolean = false, // Use updateHankeStateFlags()
    var onKaikkiPakollisetLuontiTiedot: Boolean = false, // Not saved to database
    var onTiedotLiikenneHaittaIndeksille: Boolean = false, // Not saved to database
    var onLiikenneHaittaIndeksi: Boolean = false, // Not saved to database
    var onViereisiaHankkeita: Boolean = false, // Use updateHankeStateFlags()
    var onAsiakasryhmia: Boolean = false
)
