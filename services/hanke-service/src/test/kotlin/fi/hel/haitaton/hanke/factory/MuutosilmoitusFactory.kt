package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentMetadata
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteystietoEntity
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusWithExtras
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.toJsonString
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class MuutosilmoitusFactory(
    private val muutosilmoitusService: MuutosilmoitusService,
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val yhteyshenkiloRepository: MuutosilmoituksenYhteyshenkiloRepository,
    private val hakemusFactory: HakemusFactory,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {

    fun builder(
        type: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
        status: ApplicationStatus = ApplicationStatus.DECISION,
        alluId: Int = TaydennyspyyntoFactory.DEFAULT_ALLU_ID,
    ): MuutosilmoitusBuilder {
        val hakemusEntity: HakemusEntity =
            hakemusFactory
                .builder(type)
                .withMandatoryFields()
                .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                .saveEntity()

        return builder(hakemusEntity)
    }

    fun builder(hakemus: HakemusEntity): MuutosilmoitusBuilder {
        val entity =
            MuutosilmoitusEntity(
                hakemusId = hakemus.id,
                hakemusData = hakemus.hakemusEntityData,
                sent = null,
            )
        entity.yhteystiedot =
            hakemus.yhteystiedot
                .mapValues { (_, hakemusyhteystieto) ->
                    val muutosilmoituksenYhteystieto =
                        MuutosilmoituksenYhteystietoEntity(
                            tyyppi = hakemusyhteystieto.tyyppi,
                            rooli = hakemusyhteystieto.rooli,
                            nimi = hakemusyhteystieto.nimi,
                            sahkoposti = hakemusyhteystieto.sahkoposti,
                            puhelinnumero = hakemusyhteystieto.puhelinnumero,
                            registryKey = hakemusyhteystieto.registryKey,
                            muutosilmoitus = entity,
                            yhteyshenkilot = mutableListOf(),
                        )
                    muutosilmoituksenYhteystieto.yhteyshenkilot =
                        hakemusyhteystieto.yhteyshenkilot
                            .map {
                                MuutosilmoituksenYhteyshenkiloEntity(
                                    yhteystieto = muutosilmoituksenYhteystieto,
                                    hankekayttaja = it.hankekayttaja,
                                    tilaaja = it.tilaaja,
                                )
                            }
                            .toMutableList()
                    muutosilmoituksenYhteystieto
                }
                .toMutableMap()
        return builder(entity, hakemus.hanke.id)
    }

    private fun builder(
        muutosilmoitusEntity: MuutosilmoitusEntity,
        hankeId: Int,
    ): MuutosilmoitusBuilder =
        MuutosilmoitusBuilder(
            muutosilmoitusEntity,
            hankeId,
            muutosilmoitusService,
            muutosilmoitusRepository,
            yhteyshenkiloRepository,
            hankeKayttajaFactory,
        )

    companion object {
        val DEFAULT_ID: UUID = UUID.fromString("7df81277-2e36-4082-8687-0421c20d341e")
        val DEFAULT_SENT: OffsetDateTime = OffsetDateTime.parse("2024-03-06T16:28:01Z")

        fun create(
            id: UUID = DEFAULT_ID,
            hakemusType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
            hakemusId: Long = 1L,
            sent: OffsetDateTime? = null,
            hakemusData: HakemusData = HakemusFactory.createHakemusData(hakemusType),
        ) = Muutosilmoitus(id, hakemusId, sent, hakemusData)

        fun createEntity(
            id: UUID = DEFAULT_ID,
            hakemusId: Long = ApplicationFactory.DEFAULT_APPLICATION_ID,
            sent: OffsetDateTime? = null,
            hakemusData: HakemusEntityData =
                ApplicationFactory.createBlankExcavationNotificationData(),
            yhteystiedot: Map<ApplicationContactType, MuutosilmoituksenYhteystietoEntity> = mapOf(),
        ) = MuutosilmoitusEntity(id, hakemusId, sent, hakemusData, yhteystiedot.toMutableMap())

        fun createYhteystietoEntity(
            muutosilmoitus: MuutosilmoitusEntity,
            tyyppi: CustomerType = CustomerType.COMPANY,
            rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
            nimi: String = HakemusyhteystietoFactory.DEFAULT_NIMI,
            sahkoposti: String = HakemusyhteystietoFactory.DEFAULT_SAHKOPOSTI,
            puhelinnumero: String = HakemusyhteystietoFactory.DEFAULT_PUHELINNUMERO,
            ytunnus: String? = HakemusyhteystietoFactory.DEFAULT_YTUNNUS,
        ): MuutosilmoituksenYhteystietoEntity =
            MuutosilmoituksenYhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                registryKey = ytunnus,
                muutosilmoitus = muutosilmoitus,
            )

        fun Muutosilmoitus.toUpdateRequest(): HakemusUpdateRequest =
            this.toResponse().applicationData.toJsonString().parseJson()

        fun Muutosilmoitus.withExtras(
            muutokset: List<String> = listOf(),
            liitteet: List<MuutosilmoitusAttachmentMetadata> = listOf(),
        ) = MuutosilmoitusWithExtras(id, hakemusId, sent, hakemusData, muutokset, liitteet)

        fun createYhteyshenkiloEntity(
            hankeId: Int,
            yhteystieto: MuutosilmoituksenYhteystietoEntity,
            id: UUID = UUID.randomUUID(),
            etunimi: String = HakemusyhteyshenkiloFactory.DEFAULT_ETUNIMI,
            sukunimi: String = HakemusyhteyshenkiloFactory.DEFAULT_SUKUNIMI,
            sahkoposti: String = HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI,
            puhelin: String = HakemusyhteyshenkiloFactory.DEFAULT_PUHELIN,
            tilaaja: Boolean = HakemusyhteyshenkiloFactory.DEFAULT_TILAAJA,
            permission: PermissionEntity = PermissionFactory.createEntity(),
        ): MuutosilmoituksenYhteyshenkiloEntity =
            MuutosilmoituksenYhteyshenkiloEntity(
                id = id,
                yhteystieto = yhteystieto,
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
            yhteystieto: MuutosilmoituksenYhteystietoEntity,
            hankekayttaja: HankekayttajaEntity,
            tilaaja: Boolean,
        ): MuutosilmoituksenYhteyshenkiloEntity =
            MuutosilmoituksenYhteyshenkiloEntity(
                yhteystieto = yhteystieto,
                hankekayttaja = hankekayttaja,
                tilaaja = tilaaja,
            )
    }
}
