package fi.hel.haitaton.hanke.tormaystarkastelu

import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class TormaystarkasteluRequest(
    val geometriat: FeatureCollection,
    val haittaAlkuPvm: ZonedDateTime,
    val haittaLoppuPvm: ZonedDateTime,
    val kaistaHaitta: VaikutusAutoliikenteenKaistamaariin,
    val kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus,
)
