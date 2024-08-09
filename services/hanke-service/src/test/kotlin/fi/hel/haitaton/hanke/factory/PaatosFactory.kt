package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.paatos.Paatos
import fi.hel.haitaton.hanke.paatos.PaatosEntity
import fi.hel.haitaton.hanke.paatos.PaatosRepository
import fi.hel.haitaton.hanke.paatos.PaatosTila
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class PaatosFactory(private val paatosRepository: PaatosRepository) {

    fun save(
        hakemus: Hakemus,
        hakemustunnus: String = hakemus.applicationIdentifier!!,
        tyyppi: PaatosTyyppi = PaatosTyyppi.PAATOS,
        tila: PaatosTila = PaatosTila.NYKYINEN,
    ): Paatos = saveEntity(hakemus, hakemustunnus, tyyppi, tila).toDomain()

    fun saveEntity(
        hakemus: Hakemus,
        hakemustunnus: String = hakemus.applicationIdentifier!!,
        tyyppi: PaatosTyyppi = PaatosTyyppi.PAATOS,
        tila: PaatosTila = PaatosTila.NYKYINEN,
    ): PaatosEntity =
        paatosRepository.save(createEntityForHakemus(hakemus, hakemustunnus, tyyppi, tila))

    companion object {
        fun createForHakemus(
            hakemus: Hakemus,
            hakemustunnus: String = hakemus.applicationIdentifier!!,
            tyyppi: PaatosTyyppi = PaatosTyyppi.PAATOS,
            tila: PaatosTila = PaatosTila.NYKYINEN,
        ) =
            Paatos(
                id = UUID.randomUUID(),
                hakemusId = hakemus.id,
                hakemustunnus = hakemustunnus,
                tyyppi = tyyppi,
                tila = tila,
                nimi = hakemus.applicationData.name,
                alkupaiva = hakemus.applicationData.startTime?.toLocalDate()!!,
                loppupaiva = hakemus.applicationData.endTime?.toLocalDate()!!,
                blobLocation = "${hakemus.id}/${UUID.randomUUID()}",
                size = 53,
            )

        fun createEntityForHakemus(
            hakemus: Hakemus,
            hakemustunnus: String = hakemus.applicationIdentifier!!,
            tyyppi: PaatosTyyppi = PaatosTyyppi.PAATOS,
            tila: PaatosTila = PaatosTila.NYKYINEN,
        ) =
            PaatosEntity(
                hakemusId = hakemus.id,
                hakemustunnus = hakemustunnus,
                tyyppi = tyyppi,
                tila = tila,
                nimi = hakemus.applicationData.name,
                alkupaiva = hakemus.applicationData.startTime?.toLocalDate()!!,
                loppupaiva = hakemus.applicationData.endTime?.toLocalDate()!!,
                blobLocation = "${hakemus.id}/${UUID.randomUUID()}",
                size = 53,
            )
    }
}
