package fi.hel.haitaton.hanke

import java.time.ZonedDateTime

interface Modifiable {
    var createdBy: String?
    var createdAt: ZonedDateTime?
    var modifiedBy: String?
    var modifiedAt: ZonedDateTime?
}
