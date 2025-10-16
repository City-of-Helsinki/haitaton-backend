package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.geometria.Geometriat
import kotlin.collections.get
import org.springframework.stereotype.Service

@Service
class HankeMapperService(private val hankealueService: HankealueService) {

    fun minimalDomainFrom(hankeEntity: HankeEntity): Hanke =
        minimalDomainFrom(hankeEntity, hankealueService.geometryMapFrom(hankeEntity.alueet))

    fun domainFrom(hankeEntity: HankeEntity): Hanke =
        domainFrom(hankeEntity, hankealueService.geometryMapFrom(hankeEntity.alueet))

    /**
     * Maps a HankeEntity to a minimal Hanke domain object that contains only the basic information
     * and areas.
     *
     * Needs to be called inside a transaction for lazy fields to be accessible.
     *
     * @param entity Source hanke entity.
     * @param geometriaData Since hanke only has an id reference on the actual geometries.
     */
    fun minimalDomainFrom(entity: HankeEntity, geometriaData: Map<Int, Geometriat?>): Hanke =
        with(entity) {
                Hanke(
                    id = id,
                    hankeTunnus = hankeTunnus,
                    onYKTHanke = null,
                    nimi = nimi,
                    kuvaus = null,
                    vaihe = null,
                    version = null,
                    createdBy = null,
                    createdAt = null,
                    modifiedBy = null,
                    modifiedAt = null,
                    deletionDate = null,
                    status = null,
                    generated = generated,
                )
            }
            .apply { alueet = minimalAlueList(entity.hankeTunnus, entity.alueet, geometriaData) }

    private fun minimalAlueList(
        hankeTunnus: String?,
        alueet: List<HankealueEntity>,
        geometriaData: Map<Int, Geometriat?>,
    ): MutableList<SavedHankealue> =
        alueet.map { minimalAlue(hankeTunnus, it, geometriaData[it.geometriat]) }.toMutableList()

    private fun minimalAlue(
        hankeTunnus: String?,
        entity: HankealueEntity,
        geometriat: Geometriat?,
    ) =
        with(entity) {
            SavedHankealue(
                id = id,
                hankeId = hanke?.id,
                haittaAlkuPvm = haittaAlkuPvm?.atStartOfDay(TZ_UTC),
                haittaLoppuPvm = haittaLoppuPvm?.atStartOfDay(TZ_UTC),
                geometriat = geometriat?.apply { resetFeatureProperties(hankeTunnus) },
                nimi = nimi,
                status = status,
                tormaystarkasteluTulos = entity.tormaystarkasteluTulos?.toDomain(),
            )
        }

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
                    deletionDate = deletionDate(),
                    status = status,
                    generated = generated,
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
            }

    private fun contacts(entity: HankeEntity): Map<ContactType, List<HankeYhteystieto>> =
        entity.yhteystiedot.groupBy({ it.contactType }, { it.toDomain() })

    private fun alueList(
        hankeTunnus: String?,
        alueet: List<HankealueEntity>,
        geometriaData: Map<Int, Geometriat?>,
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
                status = status,
                tormaystarkasteluTulos = entity.tormaystarkasteluTulos?.toDomain(),
                haittojenhallintasuunnitelma = haittojenhallintasuunnitelma.toMap(),
            )
        }
}
