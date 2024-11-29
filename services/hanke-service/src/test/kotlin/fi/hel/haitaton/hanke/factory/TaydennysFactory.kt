package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.taydennys.Taydennys
import fi.hel.haitaton.hanke.taydennys.TaydennysEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennysWithMuutokset
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysyhteyshenkiloEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysyhteystietoEntity
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TaydennysFactory(
    private val taydennysRepository: TaydennysRepository,
    private val taydennyspyyntoFactory: TaydennyspyyntoFactory,
    private val hakemusFactory: HakemusFactory,
) {
    fun save(
        applicationId: Long? = null,
        hakemusData: HakemusEntityData = ApplicationFactory.createCableReportApplicationData(),
    ): Taydennys {
        val taydennyspyynto =
            applicationId?.let { taydennyspyyntoFactory.saveEntity(it) } ?: throw RuntimeException()
        return TaydennysEntity(taydennyspyynto = taydennyspyynto, hakemusData = hakemusData)
            .let { taydennysRepository.save(it) }
            .toDomain()
    }

    fun saveForHakemus(
        hakemus: HakemusEntity,
        mutator: HakemusEntityData.() -> HakemusEntityData = { this },
    ): Taydennys {
        val taydennyspyynto = taydennyspyyntoFactory.saveEntity(hakemus.id)
        val entity =
            TaydennysEntity(
                taydennyspyynto = taydennyspyynto,
                hakemusData = mutator(hakemus.hakemusEntityData),
            )
        entity.yhteystiedot =
            hakemus.yhteystiedot
                .mapValues { (_, hakemusyhteystieto) ->
                    val taydennysyhteystieto =
                        TaydennysyhteystietoEntity(
                            tyyppi = hakemusyhteystieto.tyyppi,
                            rooli = hakemusyhteystieto.rooli,
                            nimi = hakemusyhteystieto.nimi,
                            sahkoposti = hakemusyhteystieto.sahkoposti,
                            puhelinnumero = hakemusyhteystieto.puhelinnumero,
                            registryKey = hakemusyhteystieto.registryKey,
                            taydennys = entity,
                            yhteyshenkilot = mutableListOf(),
                        )
                    taydennysyhteystieto.yhteyshenkilot =
                        hakemusyhteystieto.yhteyshenkilot
                            .map {
                                TaydennysyhteyshenkiloEntity(
                                    taydennysyhteystieto = taydennysyhteystieto,
                                    hankekayttaja = it.hankekayttaja,
                                    tilaaja = it.tilaaja,
                                )
                            }
                            .toMutableList()
                    taydennysyhteystieto
                }
                .toMutableMap()
        return taydennysRepository.save(entity).toDomain()
    }

    fun saveWithHakemus(
        type: ApplicationType = ApplicationType.CABLE_REPORT,
        hanke: HankeEntity? = null,
        f: (HakemusBuilder) -> Unit = {},
    ): Taydennys {
        val hakemusBuilder =
            if (hanke != null) hakemusFactory.builder(USERNAME, hanke, type)
            else {
                hakemusFactory.builder(type)
            }
        val hakemus =
            hakemusBuilder
                .withStatus(ApplicationStatus.WAITING_INFORMATION)
                .apply { f(this) }
                .saveEntity()
        return saveForHakemus(hakemus)
    }

    /** Returns updated data */
    @Transactional
    fun updateJohtoselvitysTaydennys(
        taydennys: Taydennys,
        f: JohtoselvityshakemusEntityData.() -> JohtoselvityshakemusEntityData,
    ): Taydennys =
        updateTaydennys(taydennys) {
            this as JohtoselvityshakemusEntityData
            f()
        }

    private fun updateTaydennys(
        taydennys: Taydennys,
        f: HakemusEntityData.() -> HakemusEntityData,
    ): Taydennys {
        val entity = taydennysRepository.getReferenceById(taydennys.id)
        entity.hakemusData = entity.hakemusData.f()
        return taydennysRepository.save(entity).toDomain()
    }

    companion object {
        val DEFAULT_ID: UUID = UUID.fromString("49ee9168-a1e3-45a1-8fe0-9330cd5475d3")

        fun create(
            id: UUID = DEFAULT_ID,
            taydennyspyyntoId: UUID = TaydennyspyyntoFactory.DEFAULT_ID,
            hakemusType: ApplicationType = ApplicationType.CABLE_REPORT,
            hakemusId: Long = 1L,
            hakemusData: HakemusData = HakemusFactory.createHakemusData(hakemusType),
        ) = Taydennys(id, taydennyspyyntoId, hakemusId, hakemusData)

        fun createEntity(
            id: UUID = DEFAULT_ID,
            taydennyspyynto: TaydennyspyyntoEntity = TaydennyspyyntoFactory.createEntity(),
            hakemusData: HakemusEntityData =
                ApplicationFactory.createBlankCableReportApplicationData(),
            yhteystiedot: Map<ApplicationContactType, TaydennysyhteystietoEntity> = mapOf(),
        ) = TaydennysEntity(id, taydennyspyynto, hakemusData, yhteystiedot.toMutableMap())

        fun Taydennys.toUpdateRequest(): HakemusUpdateRequest =
            this.toResponse().applicationData.toJsonString().parseJson()

        fun Taydennys.withMuutokset(muutokset: List<String>) =
            TaydennysWithMuutokset(id, taydennyspyyntoId, hakemusData, muutokset)

        fun createYhteystietoEntity(
            taydennys: TaydennysEntity,
            tyyppi: CustomerType = CustomerType.COMPANY,
            rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
            nimi: String = HakemusyhteystietoFactory.DEFAULT_NIMI,
            sahkoposti: String = HakemusyhteystietoFactory.DEFAULT_SAHKOPOSTI,
            puhelinnumero: String = HakemusyhteystietoFactory.DEFAULT_PUHELINNUMERO,
            ytunnus: String? = HakemusyhteystietoFactory.DEFAULT_YTUNNUS,
        ): TaydennysyhteystietoEntity =
            TaydennysyhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                registryKey = ytunnus,
                taydennys = taydennys,
            )

        fun createYhteyshenkiloEntity(
            hankeId: Int,
            yhteystieto: TaydennysyhteystietoEntity,
            id: UUID = UUID.randomUUID(),
            etunimi: String = HakemusyhteyshenkiloFactory.DEFAULT_ETUNIMI,
            sukunimi: String = HakemusyhteyshenkiloFactory.DEFAULT_SUKUNIMI,
            sahkoposti: String = HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI,
            puhelin: String = HakemusyhteyshenkiloFactory.DEFAULT_PUHELIN,
            tilaaja: Boolean = HakemusyhteyshenkiloFactory.DEFAULT_TILAAJA,
            permission: PermissionEntity = PermissionFactory.createEntity(),
        ): TaydennysyhteyshenkiloEntity =
            TaydennysyhteyshenkiloEntity(
                id = id,
                taydennysyhteystieto = yhteystieto,
                hankekayttaja =
                    HankekayttajaEntity(
                        id = id,
                        hankeId = hankeId,
                        etunimi = etunimi,
                        sukunimi = sukunimi,
                        sahkoposti = sahkoposti,
                        puhelin = puhelin,
                        permission = permission,
                    ),
                tilaaja = tilaaja,
            )

        fun createYhteyshenkiloEntity(
            yhteystieto: TaydennysyhteystietoEntity,
            hankekayttaja: HankekayttajaEntity,
            tilaaja: Boolean,
        ): TaydennysyhteyshenkiloEntity =
            TaydennysyhteyshenkiloEntity(
                taydennysyhteystieto = yhteystieto,
                hankekayttaja = hankekayttaja,
                tilaaja = tilaaja,
            )
    }
}
