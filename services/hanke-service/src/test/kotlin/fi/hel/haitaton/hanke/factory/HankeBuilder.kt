package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeYhteyshenkiloEntity
import fi.hel.haitaton.hanke.HankeYhteyshenkiloRepository
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.HankeYhteystietoRepository
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.ModifyGeometriaRequest
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
     * Save a hanke that has the generated field set. The hanke is created like it would be created
     * for a stand-alone johtoselvityshakemus.
     *
     * This is the best way to create a hanke with generated = true, since [save] overwrites the
     * generated tag during the update. Call through [HankeFactory.saveGenerated].
     */
    internal fun saveGenerated(
        createRequest: CreateHankeRequest = HankeFactory.createRequest()
    ): HankeEntity {
        val hanke = hankeService.createHanke(createRequest, setUpProfiiliMocks())
        val entity = hankeRepository.getReferenceById(hanke.id)
        return hankeRepository.save(entity.apply { generated = true })
    }

    fun saveWithYhteystiedot(f: HankeYhteystietoBuilder.() -> Unit): HankeEntity {
        val entity = saveEntity()
        val builder =
            HankeYhteystietoBuilder(
                entity,
                userId,
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
        omistajat = HankeYhteystietoFactory.createDifferentiated(discriminators.asList())
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
                geometriat =
                    geometriat?.let { ModifyGeometriaRequest(it.id, it.featureCollection) },
                kaistaHaitta = kaistaHaitta,
                kaistaPituusHaitta = kaistaPituusHaitta,
                meluHaitta = meluHaitta,
                polyHaitta = polyHaitta,
                tarinaHaitta = tarinaHaitta
            )
    }
}

data class HankeYhteystietoBuilder(
    val hankeEntity: HankeEntity,
    private val userId: String,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hankeYhteystietoRepository: HankeYhteystietoRepository,
    private val hankeYhteyshenkiloRepository: HankeYhteyshenkiloRepository,
) {
    fun kayttaja(
        sahkoposti: String = HankeKayttajaFactory.KAKE_EMAIL,
        userId: String = HankeKayttajaFactory.FAKE_USERID,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankekayttajaEntity =
        hankeKayttajaFactory.saveIdentifiedUser(
            hankeEntity.id,
            sahkoposti = sahkoposti,
            userId = userId,
            kayttooikeustaso = kayttooikeustaso,
        )

    fun kayttaja(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankekayttajaEntity =
        hankeKayttajaFactory.findOrSaveIdentifiedUser(
            hankeEntity.id,
            kayttajaInput,
            kayttooikeustaso = kayttooikeustaso,
        )

    fun omistaja(
        yhteystieto: HankeYhteystieto = HankeYhteystietoFactory.create(id = null),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_OMISTAJA))
    ): HankeYhteystietoEntity =
        saveYhteystieto(ContactType.OMISTAJA, yhteystieto, yhteyshenkilot.asList())

    fun omistaja(kayttooikeustaso: Kayttooikeustaso): HankeYhteystietoEntity =
        omistaja(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_OMISTAJA, kayttooikeustaso))

    fun omistaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HankeYhteystietoEntity = omistaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun rakennuttaja(
        yhteystieto: HankeYhteystieto = HankeYhteystietoFactory.create(id = null),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA))
    ): HankeYhteystietoEntity =
        saveYhteystieto(ContactType.RAKENNUTTAJA, yhteystieto, yhteyshenkilot.asList())

    fun rakennuttaja(kayttooikeustaso: Kayttooikeustaso) =
        rakennuttaja(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA, kayttooikeustaso))

    fun rakennuttaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HankeYhteystietoEntity = rakennuttaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun toteuttaja(
        yhteystieto: HankeYhteystieto = HankeYhteystietoFactory.create(id = null),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA))
    ): HankeYhteystietoEntity =
        saveYhteystieto(ContactType.TOTEUTTAJA, yhteystieto, yhteyshenkilot.asList())

    fun toteuttaja(kayttooikeustaso: Kayttooikeustaso) =
        toteuttaja(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA, kayttooikeustaso))

    fun toteuttaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HankeYhteystietoEntity = toteuttaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun muuYhteystieto(
        yhteystieto: HankeYhteystieto = HankeYhteystietoFactory.create(id = null),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA))
    ): HankeYhteystietoEntity =
        saveYhteystieto(ContactType.MUU, yhteystieto, yhteyshenkilot.asList())

    fun muuYhteystieto(kayttooikeustaso: Kayttooikeustaso) =
        muuYhteystieto(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA, kayttooikeustaso))

    fun muuYhteystieto(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HankeYhteystietoEntity = muuYhteystieto(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    private fun addYhteyshenkilo(
        yhteystietoEntity: HankeYhteystietoEntity,
        kayttaja: HankekayttajaEntity
    ) {
        hankeYhteyshenkiloRepository.save(
            HankeYhteyshenkiloEntity(hankeKayttaja = kayttaja, hankeYhteystieto = yhteystietoEntity)
        )
    }

    private fun saveYhteystieto(
        tyyppi: ContactType,
        yhteystieto: HankeYhteystieto,
        yhteyshenkilot: List<HankekayttajaEntity>,
    ): HankeYhteystietoEntity {
        val yhteystietoEntity =
            HankeYhteystietoEntity.fromDomain(yhteystieto, tyyppi, userId, hankeEntity).let {
                hankeYhteystietoRepository.save(it)
            }
        yhteyshenkilot.forEach { addYhteyshenkilo(yhteystietoEntity, it) }
        return yhteystietoEntity
    }
}
