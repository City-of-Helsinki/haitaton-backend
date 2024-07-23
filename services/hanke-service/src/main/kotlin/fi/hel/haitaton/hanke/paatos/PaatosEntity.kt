package fi.hel.haitaton.hanke.paatos

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

enum class PaatosTyyppi {
    PAATOS,
    TOIMINNALLINEN_KUNTO,
    TYO_VALMIS,
}

enum class PaatosTila {
    NYKYINEN,
    KORVATTU,
}

@Entity
@Table(name = "paatos")
class PaatosEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "application_id") val hakemusId: Long,
    val hakemustunnus: String,
    @Enumerated(EnumType.STRING) val tyyppi: PaatosTyyppi,
    @Enumerated(EnumType.STRING) val tila: PaatosTila,
    val nimi: String,
    val alkupaiva: LocalDate,
    val loppupaiva: LocalDate,
    @Column(name = "blob_location") val blobLocation: String,
    val size: Int,
) {
    fun toDomain(): Paatos =
        Paatos(
            id,
            hakemusId,
            hakemustunnus,
            tyyppi,
            tila,
            nimi,
            alkupaiva,
            loppupaiva,
            blobLocation,
            size,
        )
}
