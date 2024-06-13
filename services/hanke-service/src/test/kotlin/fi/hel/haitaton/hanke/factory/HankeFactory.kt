package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeYhteyshenkiloRepository
import fi.hel.haitaton.hanke.HankeYhteystietoRepository
import fi.hel.haitaton.hanke.HanketunnusService
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankealueFactory.TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU
import fi.hel.haitaton.hanke.factory.HankealueFactory.createHaittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.factory.HankealueFactory.createHankeAlueEntity
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_NAMES
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.tormaystarkastelu.Autoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HankeFactory(
    private val hankeService: HankeService,
    private val profiiliClient: ProfiiliClient,
    private val hanketunnusService: HanketunnusService,
    private val hankeRepository: HankeRepository,
    private val hankeYhteystietoRepository: HankeYhteystietoRepository,
    private val hankeYhteyshenkiloRepository: HankeYhteyshenkiloRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {

    fun saveMinimal(
        hankeTunnus: String = hanketunnusService.newHanketunnus(),
        nimi: String = defaultNimi,
        generated: Boolean = false,
    ): HankeEntity =
        hankeRepository.save(
            HankeEntity(hankeTunnus = hankeTunnus, nimi = nimi, generated = generated)
        )

    fun saveSeveralMinimal(n: Int): List<HankeEntity> = (1..n).map { saveMinimal() }

    /**
     * Save a minimal hanke, i.e. a hanke without any extra information and without any attached
     * users or contacts.
     *
     * Return the hanke as a domain entity for convenience.
     */
    fun saveMinimalHanke(
        hankeTunnus: String = hanketunnusService.newHanketunnus(),
        nimi: String = defaultNimi,
        generated: Boolean = false,
    ): Hanke {
        saveMinimal(hankeTunnus, nimi, generated)
        return hankeService.loadHanke(hankeTunnus)!!
    }

    /** Convenience method for storing a hanke with a hankealue. */
    fun saveWithAlue(userId: String = USERNAME): HankeEntity =
        builder(userId).withHankealue().saveEntity()

    /**
     * Save a hanke that has the generated field set. The hanke is created like it would be created
     * for a stand-alone johtoselvityshakemus.
     */
    fun saveGenerated(
        createRequest: CreateHankeRequest = createRequest(),
        userId: String = USERNAME,
    ): HankeEntity = builder(userId).saveGenerated(createRequest)

    fun builder(userId: String = USERNAME): HankeBuilder {
        val hanke =
            create(
                nimi = defaultNimi,
                kuvaus = defaultKuvaus,
                vaihe = Hankevaihe.OHJELMOINTI,
            )
        return HankeBuilder(
            hanke,
            DEFAULT_HANKE_PERUSTAJA,
            userId,
            DEFAULT_NAMES,
            hankeService,
            hankeRepository,
            profiiliClient,
            hankeKayttajaFactory,
            hankeYhteystietoRepository,
            hankeYhteyshenkiloRepository,
        )
    }

    fun yhteystietoBuilderFrom(hanke: HankeEntity): HankeYhteystietoBuilder =
        HankeYhteystietoBuilder(
            hanke,
            hanke.createdByUserId!!,
            hankeKayttajaFactory,
            hankeYhteystietoRepository,
            hankeYhteyshenkiloRepository
        )

    fun addYhteystiedotTo(hanke: HankeEntity, f: HankeYhteystietoBuilder.() -> Unit) {
        yhteystietoBuilderFrom(hanke).f()
    }

    companion object {

        const val defaultHankeTunnus = "HAI21-1"
        const val defaultNimi = "Hämeentien perusparannus ja katuvalot"
        const val defaultKuvaus = "lorem ipsum dolor sit amet..."
        const val defaultId = 123
        const val defaultUser = "Risto"
        val DEFAULT_HANKE_PERUSTAJA = HankePerustaja("pertti@perustaja.test", "0401234567")

        /**
         * Create a simple Hanke with test values. The default values can be overridden with named
         * parameters.
         *
         * Example:
         * ```
         * HankeFactory.create(id = null, hankeTunnus = null, nimi = "Testihanke")
         * ```
         */
        fun create(
            id: Int = defaultId,
            hankeTunnus: String = defaultHankeTunnus,
            nimi: String = defaultNimi,
            kuvaus: String? = defaultKuvaus,
            vaihe: Hankevaihe? = Hankevaihe.OHJELMOINTI,
            version: Int? = 1,
            createdBy: String? = defaultUser,
            createdAt: ZonedDateTime? = DateFactory.getStartDatetime(),
            hankeStatus: HankeStatus = HankeStatus.DRAFT,
        ): Hanke =
            Hanke(
                id = id,
                hankeTunnus = hankeTunnus,
                onYKTHanke = true,
                nimi = nimi,
                kuvaus = kuvaus,
                vaihe = vaihe,
                version = version,
                createdBy = createdBy,
                createdAt = createdAt,
                modifiedBy = null,
                modifiedAt = null,
                status = hankeStatus,
            )

        fun createMinimalEntity(
            id: Int = defaultId,
            hankeTunnus: String = defaultHankeTunnus,
            nimi: String = defaultNimi,
            generated: Boolean = false,
        ) = HankeEntity(id = id, hankeTunnus = hankeTunnus, nimi = nimi, generated = generated)

        fun createEntity(mockId: Int = 1): HankeEntity =
            HankeEntity(
                    id = mockId,
                    status = HankeStatus.DRAFT,
                    hankeTunnus = defaultHankeTunnus,
                    nimi = defaultNimi,
                    kuvaus = defaultKuvaus,
                    vaihe = Hankevaihe.SUUNNITTELU,
                    onYKTHanke = true,
                    version = 0,
                    createdByUserId = defaultUser,
                    createdAt = DateFactory.getStartDatetime().toLocalDateTime(),
                    modifiedByUserId = defaultUser,
                    modifiedAt = DateFactory.getEndDatetime().toLocalDateTime(),
                    generated = false,
                )
                .apply {
                    yhteystiedot =
                        mutableListOf(
                            HankeYhteystietoFactory.createEntity(1, OMISTAJA, this),
                            HankeYhteystietoFactory.createEntity(2, TOTEUTTAJA, this),
                            HankeYhteystietoFactory.createEntity(3, RAKENNUTTAJA, this),
                            HankeYhteystietoFactory.createEntity(4, MUU, this)
                        )
                    alueet =
                        mutableListOf(createHankeAlueEntity(mockId = mockId, hankeEntity = this))
                    liitteet =
                        mutableListOf(
                            HankeAttachmentFactory.createEntity(
                                hanke = this,
                                createdByUser = defaultUser
                            )
                        )
                }

        fun createRequest(
            nimi: String = defaultNimi,
            perustaja: HankePerustaja = DEFAULT_HANKE_PERUSTAJA
        ): CreateHankeRequest = CreateHankeRequest(nimi, perustaja)

        /**
         * Add a hankealue with haitat to a test Hanke.
         *
         * Example:
         * ```
         * HankeFactory.create().withHankealue()
         * ```
         */
        fun Hanke.withHankealue(
            nimi: String = "$HANKEALUE_DEFAULT_NAME 1",
            haittaAlkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
            haittaLoppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
            haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma? =
                createHaittojenhallintasuunnitelma(),
        ): Hanke {
            this.tyomaaKatuosoite = "Testikatu 1"
            this.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
            this.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
            val alue =
                HankealueFactory.create(
                    hankeId = this.id,
                    nimi = nimi,
                    haittaAlkuPvm = haittaAlkuPvm,
                    haittaLoppuPvm = haittaLoppuPvm,
                    haittojenhallintasuunnitelma = haittojenhallintasuunnitelma,
                )
            this.alueet.add(alue)

            return this
        }

        fun Hanke.withTormaystarkasteluTulos(
            autoliikenne: Autoliikenneluokittelu = TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU,
            pyoraliikenneindeksi: Float = 1f,
            linjaautoliikenneindeksi: Float = 1f,
            raitioliikenneindeksi: Float = 1f,
        ): Hanke {
            this.tormaystarkasteluTulos =
                TormaystarkasteluTulos(
                    autoliikenne,
                    pyoraliikenneindeksi,
                    linjaautoliikenneindeksi,
                    raitioliikenneindeksi,
                )
            return this
        }

        /**
         * Add yhteystiedot to a test Hanke. Generates the yhteystiedot with
         * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating
         * the yhteystiedot from each other.
         *
         * Without parameters, will add one contact for each role.
         *
         * You can provide a lambda for mutating all the generated yhteystieto after creation.
         *
         * Examples:
         * ```
         * HankeFactory.create().withYhteystiedot()
         *
         * HankeFactory.create().withYhteystiedot(
         *     omistajat = listOf(1,2),
         *     rakennuttajat = listOf(3,4),
         *     toteuttajat = listOf(2,5),
         *     muut = listOf(3,2),
         * )
         * ```
         *
         * Using the withGeneratedX methods is probably cleaner than overriding the parameters of
         * this method.
         */
        fun Hanke.withYhteystiedot(
            omistajat: List<Int> = listOf(1),
            rakennuttajat: List<Int> = listOf(2),
            toteuttajat: List<Int> = listOf(3),
            muut: List<Int> = listOf(4),
            mutator: HankeYhteystieto.() -> Unit = {},
        ): Hanke {
            this.omistajat.addAll(HankeYhteystietoFactory.createDifferentiated(omistajat, mutator))
            this.rakennuttajat.addAll(
                HankeYhteystietoFactory.createDifferentiated(rakennuttajat, mutator)
            )
            this.toteuttajat.addAll(
                HankeYhteystietoFactory.createDifferentiated(toteuttajat, mutator)
            )
            this.muut.addAll(HankeYhteystietoFactory.createDifferentiated(muut, mutator))
            return this
        }

        fun Hanke.withOmistaja(i: Int, id: Int? = i, vararg yhteyshenkilo: Yhteyshenkilo): Hanke {
            omistajat.add(
                HankeYhteystietoFactory.createDifferentiated(i, id, yhteyshenkilo.toList())
            )
            return this
        }

        fun Hanke.withRakennuttaja(
            i: Int,
            id: Int? = i,
            vararg yhteyshenkilo: Yhteyshenkilo
        ): Hanke {
            rakennuttajat.add(
                HankeYhteystietoFactory.createDifferentiated(i, id, yhteyshenkilo.toList())
            )
            return this
        }

        fun Hanke.withToteuttaja(i: Int, id: Int? = i, vararg yhteyshenkilo: Yhteyshenkilo): Hanke {
            toteuttajat.add(
                HankeYhteystietoFactory.createDifferentiated(i, id, yhteyshenkilo.toList())
            )
            return this
        }

        fun Hanke.withMuuYhteystieto(
            i: Int,
            id: Int? = i,
            vararg yhteyshenkilo: Yhteyshenkilo
        ): Hanke {
            muut.add(HankeYhteystietoFactory.createDifferentiated(i, id, yhteyshenkilo.toList()))
            return this
        }
    }
}
