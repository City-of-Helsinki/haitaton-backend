package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos

object HankeMapper {

    /**
     * Needs to be called inside a transaction for lazy fields to be accessible.
     *
     * @param entity Source hanke entity.
     * @param geometriaData Since hanke only has an id reference on the actual geometries.
     */
    fun domainFrom(entity: HankeEntity, geometriaData: Map<Int, Geometriat?>): Hanke =
        with(entity) {
                Hanke(
                    id = id,
                    hankeTunnus = hankeTunnus,
                    onYKTHanke = onYKTHanke,
                    nimi = nimi,
                    kuvaus = kuvaus,
                    vaihe = vaihe,
                    version = version,
                    createdBy = createdByUserId,
                    createdAt = createdAt?.zonedDateTime(),
                    modifiedBy = modifiedByUserId,
                    modifiedAt = modifiedAt?.zonedDateTime(),
                    status = status,
                    generated = generated
                )
            }
            .apply {
                val contacts = contacts(entity)
                omistajat = contacts[OMISTAJA].orEmpty().toMutableList()
                rakennuttajat = contacts[RAKENNUTTAJA].orEmpty().toMutableList()
                toteuttajat = contacts[TOTEUTTAJA].orEmpty().toMutableList()
                muut = contacts[MUU].orEmpty().toMutableList()
                tyomaaKatuosoite = entity.tyomaaKatuosoite
                tyomaaTyyppi = entity.tyomaaTyyppi
                alueet = alueList(entity.hankeTunnus, entity.alueet, geometriaData)
                tormaystarkasteluTulos = tormaystarkasteluTulos(entity)
            }

    private fun contacts(entity: HankeEntity): Map<ContactType, List<HankeYhteystieto>> =
        entity.listOfHankeYhteystieto.groupBy({ it.contactType }, { it.toDomain() })

    private fun alueList(
        hankeTunnus: String?,
        alueet: MutableList<HankealueEntity>,
        geometriaData: Map<Int, Geometriat?>
    ): MutableList<SavedHankealue> =
        alueet.map { alue(hankeTunnus, it, geometriaData[it.geometriat]) }.toMutableList()

    private fun alue(hankeTunnus: String?, entity: HankealueEntity, geometriat: Geometriat?) =
        with(entity) {
            SavedHankealue(
                id = id,
                hankeId = hanke?.id,
                haittaAlkuPvm = haittaAlkuPvm?.atStartOfDay(TZ_UTC),
                haittaLoppuPvm = haittaLoppuPvm?.atStartOfDay(TZ_UTC),
                geometriat = geometriat?.apply { resetFeatureProperties(hankeTunnus) },
                kaistaHaitta = kaistaHaitta,
                kaistaPituusHaitta = kaistaPituusHaitta,
                meluHaitta = meluHaitta,
                polyHaitta = polyHaitta,
                tarinaHaitta = tarinaHaitta,
                nimi = nimi,
            )
        }

    private fun tormaystarkasteluTulos(entity: HankeEntity) =
        entity.tormaystarkasteluTulokset.firstOrNull()?.let {
            TormaystarkasteluTulos(
                it.autoliikenne,
                it.pyoraliikenne,
                it.linjaautoliikenne,
                it.raitioliikenne
            )
        }
}
