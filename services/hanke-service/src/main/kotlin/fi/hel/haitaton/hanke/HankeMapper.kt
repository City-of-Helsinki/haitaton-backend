package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.geometria.GeometriatService
import org.springframework.stereotype.Component

@Component
class HankeMapper(private val geometriatService: GeometriatService) {

    fun domainFromEntity(entity: HankeEntity): Hanke =
        with(entity) {
            Hanke(
                    id = id,
                    hankeTunnus = hankeTunnus,
                    onYKTHanke = onYKTHanke,
                    nimi = nimi,
                    kuvaus = kuvaus,
                    vaihe = vaihe,
                    suunnitteluVaihe = suunnitteluVaihe,
                    version = version,
                    createdBy = createdByUserId,
                    createdAt = createdAt?.zonedDateTime(),
                    modifiedBy = modifiedByUserId,
                    modifiedAt = modifiedAt?.zonedDateTime(),
                    status = status,
                    generated = generated
                )
                .apply {
                    val contacts = domainContacts(entity)
                    omistajat = contacts[OMISTAJA].orEmpty().toMutableList()
                    rakennuttajat = contacts[RAKENNUTTAJA].orEmpty().toMutableList()
                    toteuttajat = contacts[TOTEUTTAJA].orEmpty().toMutableList()
                    muut = contacts[MUU].orEmpty().toMutableList()
                    tyomaaKatuosoite = entity.tyomaaKatuosoite
                    tyomaaTyyppi = entity.tyomaaTyyppi
                    alueet = entity.listOfHankeAlueet.map { domainFromEntity(it) }.toMutableList()
                }
        }

    private fun domainContacts(
        entity: HankeEntity
    ): Map<ContactType, MutableList<HankeYhteystieto>> =
        entity.listOfHankeYhteystieto.groupBy({ it.contactType }, { it.toDomain() }).mapValues {
            (_, contacts) ->
            contacts.toMutableList()
        }

    private fun domainFromEntity(entity: HankealueEntity) =
        with(entity) {
            Hankealue(
                id = id,
                hankeId = hanke?.id,
                haittaAlkuPvm = haittaAlkuPvm?.atStartOfDay(TZ_UTC),
                haittaLoppuPvm = haittaLoppuPvm?.atStartOfDay(TZ_UTC),
                geometriat = geometriat?.let { geometriatService.getGeometriat(it) }
            )
        }
}
