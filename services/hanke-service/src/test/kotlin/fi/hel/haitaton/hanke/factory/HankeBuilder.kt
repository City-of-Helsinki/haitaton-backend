package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.HankeYhteyshenkiloRepository
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.HankeYhteystietoRepository
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.ModifyHankeRequest
import fi.hel.haitaton.hanke.domain.ModifyHankeYhteystietoRequest
import fi.hel.haitaton.hanke.domain.ModifyHankealueRequest
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_NAMES
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.profiili.Names
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.userId
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.core.context.SecurityContext

data class HankeBuilder(
    private val hanke: Hanke,
    private val perustaja: HankePerustaja,
    private val userId: String,
    private val names: Names = DEFAULT_NAMES,
    private val hankeService: HankeService,
    private val hankeRepository: HankeRepository,
    private val mockProfiiliClient: ProfiiliClient,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hankeYhteystietoRepository: HankeYhteystietoRepository,
    private val hankeYhteyshenkiloRepository: HankeYhteyshenkiloRepository,
) {
    /**
     * Create this hanke in the state it would be after creating it for the first time. Only name is
     * saved along with some generated fields.
     *
     * A founder is created as a HankeKayttaja with KAIKKI_OIKEUDET permission to the hanke. The
     * email and phonenumber of the founder are read from the `perustaja` field, but first and last
     * name are read from `names` field. This mimics how founder information is given partly from
     * the UI and partly read from Profiili.
     */
    fun create(): Hanke {
        val request = CreateHankeRequest(hanke.nimi, perustaja)
        return hankeService.createHanke(request, setUpProfiiliMocks())
    }

    /**
     * Create this hanke and then update it to give it fuller information. This method does an
     * actual update, so it will set modifiedBy and modifiedAt columns and bump version up to 1.
     */
    fun save(): Hanke {
        val createdHanke = create()
        return hankeService.updateHanke(
            createdHanke.hankeTunnus,
            hanke.toModifyRequest(idMapper = { null })
        )
    }

    /** Save the entity with [save], and - for convenience - get the saved entity from DB. */
    fun saveEntity(): HankeEntity = hankeRepository.getReferenceById(save().id)

    /**
     * Save a standalone cable report application from this hanke with the given application data.
     * This is the best way to create a hanke with generated = true, since [save] overwrites the
     * generated tag during the update.
     */
    fun saveAsGenerated(
        applicationData: CableReportApplicationData =
            ApplicationFactory.createCableReportApplicationData()
    ): Pair<Application, Hanke> {
        val application =
            hankeService.generateHankeWithApplication(
                CableReportWithoutHanke(ApplicationType.CABLE_REPORT, applicationData),
                setUpProfiiliMocks()
            )
        val hanke = hankeService.loadHanke(application.hankeTunnus)!!
        return Pair(application, hanke)
    }

    fun saveWithYhteystiedot(f: HankeYhteystietoBuilder.() -> Unit): HankeEntity {
        val entity = saveEntity()
        val builder =
            HankeYhteystietoBuilder(
                entity,
                userId,
                hankeService,
                hankeKayttajaFactory,
                hankeYhteystietoRepository,
                hankeYhteyshenkiloRepository,
            )
        builder.f()
        return entity
    }

    fun withYhteystiedot(): HankeBuilder = applyToHanke {
        omistajat = mutableListOf(HankeYhteystietoFactory.createDifferentiated(1))
        rakennuttajat = mutableListOf(HankeYhteystietoFactory.createDifferentiated(2))
        toteuttajat = mutableListOf(HankeYhteystietoFactory.createDifferentiated(3))
        muut = mutableListOf(HankeYhteystietoFactory.createDifferentiated(4))
    }

    fun withGeneratedOmistaja(i: Int = 1) = withGeneratedOmistajat(i)

    fun withGeneratedOmistajat(vararg discriminators: Int) = applyToHanke {
        omistajat = HankeYhteystietoFactory.createDifferentiated(discriminators.toList())
    }

    fun withGeneratedRakennuttaja(i: Int = 1) = applyToHanke {
        rakennuttajat = mutableListOf(HankeYhteystietoFactory.createDifferentiated(i, id = null))
    }

    fun withHankealue(alue: SavedHankealue = HankealueFactory.create()) = applyToHanke {
        alueet.add(alue)
        tyomaaKatuosoite = "Testikatu 1"
        tyomaaTyyppi = mutableSetOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
    }

    fun withPerustaja(perustaja: HankekayttajaInput): HankeBuilder =
        this.copy(
            perustaja =
                HankePerustaja(sahkoposti = perustaja.email, puhelinnumero = perustaja.puhelin),
            names =
                Names(
                    firstName = perustaja.etunimi,
                    lastName = perustaja.sukunimi,
                    givenName = perustaja.etunimi
                )
        )

    fun withTyomaaKatuosoite(tyomaaKatuosoite: String?): HankeBuilder = applyToHanke {
        this.tyomaaKatuosoite = tyomaaKatuosoite
    }

    fun withTyomaaTyypit(vararg tyypit: TyomaaTyyppi): HankeBuilder = applyToHanke {
        tyomaaTyyppi.addAll(tyypit)
    }

    private fun applyToHanke(f: Hanke.() -> Unit) = apply { hanke.apply { f() } }

    private fun setUpProfiiliMocks(): SecurityContext {
        val securityContext: SecurityContext = mockk()
        every { securityContext.userId() } returns userId
        every { mockProfiiliClient.getVerifiedName(any()) } returns names
        return securityContext
    }

    companion object {
        fun Hanke.toModifyRequest(idMapper: (Int?) -> Int? = { it }) =
            ModifyHankeRequest(
                onYKTHanke = onYKTHanke,
                nimi = nimi,
                kuvaus = kuvaus,
                vaihe = vaihe,
                omistajat = omistajat.map { it.toModifyRequest(id = idMapper(it.id)) },
                rakennuttajat = rakennuttajat.map { it.toModifyRequest(id = idMapper(it.id)) },
                toteuttajat = toteuttajat.map { it.toModifyRequest(id = idMapper(it.id)) },
                muut = muut.map { it.toModifyRequest(id = idMapper(it.id)) },
                tyomaaKatuosoite = tyomaaKatuosoite,
                tyomaaTyyppi = tyomaaTyyppi,
                alueet = alueet.map { it.toModifyRequest(id = idMapper(it.id)) },
            )

        fun HankeYhteystieto.toModifyRequest(id: Int? = this.id) =
            ModifyHankeYhteystietoRequest(
                id = id,
                nimi = nimi,
                email = email,
                puhelinnumero = puhelinnumero,
                organisaatioNimi = organisaatioNimi,
                osasto = osasto,
                rooli = rooli,
                tyyppi = tyyppi,
                ytunnus = ytunnus,
                yhteyshenkilot = yhteyshenkilot.map { it.id }
            )

        fun SavedHankealue.toModifyRequest(id: Int? = this.id) =
            ModifyHankealueRequest(
                id = id,
                nimi = nimi,
                haittaAlkuPvm = haittaAlkuPvm,
                haittaLoppuPvm = haittaLoppuPvm,
                geometriat = geometriat,
                kaistaHaitta = kaistaHaitta,
                kaistaPituusHaitta = kaistaPituusHaitta,
                meluHaitta = meluHaitta,
                polyHaitta = polyHaitta,
                tarinaHaitta = tarinaHaitta
            )
    }
}

data class HankeYhteystietoBuilder(
    private val hankeEntity: HankeEntity,
    private val userId: String,
    private val hankeService: HankeService? = null,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hankeYhteystietoRepository: HankeYhteystietoRepository,
    private val hankeYhteyshenkiloRepository: HankeYhteyshenkiloRepository,
) {
    fun kayttaja(sahkoposti: String = HankeKayttajaFactory.KAKE_EMAIL): HankekayttajaEntity =
        hankeKayttajaFactory.saveIdentifiedUser(hankeEntity.id, sahkoposti = sahkoposti)

    fun omistaja(
        omistaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_OMISTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HankeYhteystietoEntity) -> Unit = defaultYhteyshenkilo(omistaja, kayttooikeustaso),
    ): HankeYhteystietoBuilder {
        f(saveYhteystieto(ContactType.OMISTAJA))
        return this
    }

    fun rakennuttaja(
        rakennuttaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HankeYhteystietoEntity) -> Unit = defaultYhteyshenkilo(rakennuttaja, kayttooikeustaso),
    ): HankeYhteystietoBuilder {
        f(saveYhteystieto(ContactType.RAKENNUTTAJA))
        return this
    }

    fun toteuttaja(
        toteuttaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HankeYhteystietoEntity) -> Unit = defaultYhteyshenkilo(toteuttaja, kayttooikeustaso),
    ): HankeYhteystietoBuilder {
        f(saveYhteystieto(ContactType.TOTEUTTAJA))
        return this
    }

    fun muuYhteystieto(
        muu: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HankeYhteystietoEntity) -> Unit = defaultYhteyshenkilo(muu, kayttooikeustaso),
    ): HankeYhteystietoBuilder {
        f(saveYhteystieto(ContactType.MUU))
        return this
    }

    private fun addYhteyshenkilo(
        yhteystietoEntity: HankeYhteystietoEntity,
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ) {
        val kayttaja =
            hankeKayttajaFactory.findOrSaveIdentifiedUser(
                hankeEntity.id,
                kayttajaInput,
                kayttooikeustaso
            )
        addYhteyshenkilo(yhteystietoEntity, kayttaja)
    }

    fun addYhteyshenkilo(yhteystietoEntity: HankeYhteystietoEntity, kayttaja: HankekayttajaEntity) {
        hankeYhteyshenkiloRepository.save(
            HankeYhteyshenkiloEntity(hankeKayttaja = kayttaja, hankeYhteystieto = yhteystietoEntity)
        )
    }

    private fun saveYhteystieto(tyyppi: ContactType): HankeYhteystietoEntity {
        val yhteystieto = HankeYhteystietoFactory.create(id = null)
        val yhteystietoEntity =
            HankeYhteystietoEntity.fromDomain(yhteystieto, tyyppi, userId, hankeEntity).let {
                hankeYhteystietoRepository.save(it)
            }
        return yhteystietoEntity
    }

    private fun defaultYhteyshenkilo(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso,
    ): (HankeYhteystietoEntity) -> Unit = { yhteystieto ->
        addYhteyshenkilo(yhteystieto, kayttajaInput, kayttooikeustaso)
    }
}
