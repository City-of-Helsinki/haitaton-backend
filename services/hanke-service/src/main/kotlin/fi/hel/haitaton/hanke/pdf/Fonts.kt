package fi.hel.haitaton.hanke.pdf

import java.awt.Color
import org.openpdf.text.Font
import org.openpdf.text.pdf.BaseFont

private val baseRegularFont =
    BaseFont.createFont(
        "pdf-assets/liberation-sans/LiberationSans-Regular.ttf",
        BaseFont.IDENTITY_H,
        BaseFont.EMBEDDED,
    )
private val baseBoldFont =
    BaseFont.createFont(
        "pdf-assets/liberation-sans/LiberationSans-Bold.ttf",
        BaseFont.IDENTITY_H,
        BaseFont.EMBEDDED,
    )

/**
 * Font used for the top header of document, text above the main title. The biggest title we have.
 */
val headerFont = Font(baseBoldFont, pxToPt(18), Font.NORMAL, Color.BLACK)

/** Font used for the main title of document. The biggest title we have. */
val titleFont = Font(baseRegularFont, pxToPt(32), Font.NORMAL, Color.BLACK)
/** Font used for subtitle under the main title. */
val subtitleFont = Font(baseRegularFont, pxToPt(20), Font.NORMAL, Color.BLACK)
/** Font used for the title of a section of data. */
val sectionTitleFont = Font(baseBoldFont, pxToPt(20), Font.NORMAL, Color.BLACK)

/** Font used for the left header column of a data section. */
val rowHeaderFont = Font(baseBoldFont, pxToPt(14), Font.NORMAL, Color.BLACK)
/** Font used for the left header column of a data section. */
val textFont = Font(baseRegularFont, pxToPt(14), Font.NORMAL, Color.BLACK)

/** Font used for showing "Toimet haittojen hallintaan" in haittojenhallintasuunnitelma. */
val toimetFont = Font(baseBoldFont, pxToPt(16), Font.NORMAL, Color.BLACK)

/** Fonts used when the background is set to a nuisance color. */
val whiteNuisanceFont = Font(baseRegularFont, pxToPt(16), Font.NORMAL, Color.WHITE)
val blackNuisanceFont = Font(baseRegularFont, pxToPt(16), Font.NORMAL, Color.BLACK)
