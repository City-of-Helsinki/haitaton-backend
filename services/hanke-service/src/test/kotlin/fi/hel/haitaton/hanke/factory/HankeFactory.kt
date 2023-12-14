package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HanketunnusService
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.createEntity
import fi.hel.haitaton.hanke.factory.HankealueFactory.createHankeAlueEntity
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_NAMES
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HankeFactory(
    private val hankeService: HankeService,
    private val profiiliClient: ProfiiliClient,
    private val hanketunnusService: HanketunnusService,
    private val hankeRepository: HankeRepository,
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

    fun builder(userId: String): HankeBuilder {
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
        )
    }

    companion object {

        const val defaultHankeTunnus = "HAI21-1"
        const val defaultNimi = "Hämeentien perusparannus ja katuvalot"
        const val defaultKuvaus = "lorem ipsum dolor sit amet..."
        const val defaultId = 123
        const val defaultUser = "Risto"
        val DEFAULT_HANKE_PERUSTAJA = HankePerustaja("pertti@perustaja.test", "0401234567")

        fun builder() = HankeBuilder(create(), DEFAULT_HANKE_PERUSTAJA, defaultUser)

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
                    listOfHankeYhteystieto =
                        mutableListOf(
                            createEntity(id = 1, contactType = OMISTAJA, hanke = this),
                            createEntity(id = 2, contactType = TOTEUTTAJA, hanke = this),
                            createEntity(id = 3, contactType = RAKENNUTTAJA, hanke = this),
                            createEntity(id = 4, contactType = MUU, hanke = this)
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
                    tormaystarkasteluTulokset = mutableListOf(tormaysTarkastelu(hankeEntity = this))
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
                )
            this.alueet.add(alue)

            return this
        }

        fun Hanke.withTormaystarkasteluTulos(
            autoliikenneindeksi: Float = 1f,
            pyoraliikenneindeksi: Float = 1f,
            linjaautoliikenneindeksi: Float = 1f,
            raitioliikenneindeksi: Float = 1f,
        ): Hanke {
            this.tormaystarkasteluTulos =
                TormaystarkasteluTulos(
                    autoliikenneindeksi,
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

        /**
         * Add a number of omistaja to a hanke. Generates the yhteystiedot with
         * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating
         * the yhteystiedot from each other.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedOmistajat(listOf(1,2))
         * ```
         */
        fun Hanke.withGeneratedOmistajat(
            ids: List<Int>,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke {
            omistajat.addAll(HankeYhteystietoFactory.createDifferentiated(ids, mutator))
            return this
        }

        /**
         * Same as [Hanke.withGeneratedOmistajat] but using varargs instead of a list.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedOmistajat(1,2)
         * ```
         */
        fun Hanke.withGeneratedOmistajat(
            vararg ids: Int,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke = withGeneratedOmistajat(ids.toList(), mutator)

        /**
         * Same as [Hanke.withGeneratedOmistajat] but adds a single omistaja.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedOmistaja(1)
         * ```
         */
        fun Hanke.withGeneratedOmistaja(id: Int, mutator: HankeYhteystieto.() -> Unit = {}): Hanke =
            withGeneratedOmistajat(listOf(id), mutator)

        /**
         * Add a number of rakennuttaja to a hanke. Generates the yhteystiedot with
         * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating
         * the yhteystiedot from each other.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedRakennuttajat(listOf(1,2))
         * ```
         */
        fun Hanke.withGeneratedRakennuttajat(
            ids: List<Int>,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke {
            rakennuttajat.addAll(HankeYhteystietoFactory.createDifferentiated(ids, mutator))
            return this
        }

        /**
         * Same as [Hanke.withGeneratedRakennuttajat] but using varargs instead of a list.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedRakennuttajat(1,2)
         * ```
         */
        fun Hanke.withGeneratedRakennuttajat(
            vararg ids: Int,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke = withGeneratedRakennuttajat(ids.toList(), mutator)

        /**
         * Same as [Hanke.withGeneratedRakennuttajat] but adds a single rakennuttaja.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedRakennuttaja(1)
         * ```
         */
        fun Hanke.withGeneratedRakennuttaja(
            id: Int,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke = withGeneratedRakennuttajat(listOf(id), mutator)

        /**
         * Add a number of toteuttaja to a hanke. Generates the yhteystiedot with
         * [HankeYhteystietoFactory.createDifferentiated] using the given ints for differentiating
         * the yhteystiedot from each other.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedToteuttajat(listOf(1,2))
         * ```
         */
        fun Hanke.withGeneratedToteuttajat(
            ids: List<Int>,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke {
            toteuttajat.addAll(HankeYhteystietoFactory.createDifferentiated(ids, mutator))
            return this
        }

        /**
         * Same as [Hanke.withGeneratedToteuttajat] but using varargs instead of a list.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedToteuttajat(1,2)
         * ```
         */
        fun Hanke.withGeneratedToteuttajat(
            vararg ids: Int,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke = withGeneratedToteuttajat(ids.toList(), mutator)

        /**
         * Same as [Hanke.withGeneratedToteuttajat] but adds a single toteuttaja.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedToteuttaja(1)
         * ```
         */
        fun Hanke.withGeneratedToteuttaja(
            id: Int,
            mutator: HankeYhteystieto.() -> Unit = {}
        ): Hanke = withGeneratedToteuttajat(listOf(id), mutator)

        private fun tormaysTarkastelu(
            id: Int = 1,
            hankeEntity: HankeEntity
        ): TormaystarkasteluTulosEntity =
            TormaystarkasteluTulosEntity(
                id = id,
                autoliikenne = 1.25f,
                pyoraliikenne = 2.5f,
                linjaautoliikenne = 3.75f,
                raitioliikenne = 3.75f,
                hanke = hankeEntity,
            )
    }
}
