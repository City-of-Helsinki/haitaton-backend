package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.ContactType.MUU
import fi.hel.haitaton.hanke.ContactType.OMISTAJA
import fi.hel.haitaton.hanke.ContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.ContactType.TOTEUTTAJA
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.HankeStatus.DRAFT
import fi.hel.haitaton.hanke.HanketunnusService
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.Vaihe.SUUNNITTELU
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Perustaja
import fi.hel.haitaton.hanke.factory.AttachmentFactory.Companion.hankeAttachmentEntity
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.createEntity
import fi.hel.haitaton.hanke.factory.HankealueFactory.createHankeAlueEntity
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulosEntity
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HankeFactory(
    private val hankeService: HankeService,
    private val hanketunnusService: HanketunnusService,
    private val hankeRepository: HankeRepository,
) {

    /**
     * Create a new hanke and save it to database.
     *
     * Needs a mock user set up, since `hankeService.createHanke` calls currentUserId() directly.
     */
    fun save(
        nimi: String = defaultNimi,
        vaihe: Vaihe? = Vaihe.OHJELMOINTI,
        tyomaaKatuosoite: String? = null,
        tyomaaTyyppi: Set<TyomaaTyyppi>? = null,
    ) =
        hankeService.createHanke(
            CreateHankeRequest(
                nimi = nimi,
                vaihe = vaihe,
                tyomaaKatuosoite = tyomaaKatuosoite,
                tyomaaTyyppi = tyomaaTyyppi,
            )
        )

    /**
     * Save a new hanke using HankeService. Then get it as an entity.
     *
     * The service method creates a hankeTunnus and does other initialization, so we want to run it,
     * even though we want to return the entity, not the domain object.
     */
    fun saveEntity(
        nimi: String = defaultNimi,
        vaihe: Vaihe? = Vaihe.OHJELMOINTI,
    ): HankeEntity {
        val hanke = save(nimi, vaihe)
        return hankeRepository.getReferenceById(hanke.id)
    }

    fun saveMinimal(
        hankeTunnus: String = hanketunnusService.newHanketunnus(),
        nimi: String = defaultNimi,
        generated: Boolean = false,
    ): HankeEntity =
        hankeRepository.save(
            HankeEntity(hankeTunnus = hankeTunnus, nimi = nimi, generated = generated)
        )

    fun saveSeveralMinimal(n: Int): List<HankeEntity> = (1..n).map { saveMinimal() }

    fun saveGenerated(
        cableReportWithoutHanke: CableReportWithoutHanke =
            AlluDataFactory.cableReportWithoutHanke(),
        userId: String
    ): Hanke {
        val application = hankeService.generateHankeWithApplication(cableReportWithoutHanke, userId)
        return hankeService.loadHanke(application.hankeTunnus)!!
    }

    fun createRequest(
        nimi: String = defaultNimi,
        onYKTHanke: Boolean? = true,
        kuvaus: String? = defaultKuvaus,
        vaihe: Vaihe? = Vaihe.OHJELMOINTI,
        tyomaaKatuosoite: String? = null,
        tyomaaTyyppi: Set<TyomaaTyyppi>? = null,
    ) =
        Companion.createRequest(
                nimi,
                onYKTHanke,
                kuvaus,
                vaihe,
                tyomaaKatuosoite,
                tyomaaTyyppi,
            )
            .copy(hankeService = hankeService)

    companion object {

        const val defaultHankeTunnus = "HAI21-1"
        const val defaultNimi = "Hämeentien perusparannus ja katuvalot"
        const val defaultKuvaus = "lorem ipsum dolor sit amet..."
        const val defaultId = 123
        const val defaultUser = "Risto"
        val defaultPerustaja = Perustaja("Pertti Perustaja", "foo@bar.com")

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
            vaihe: Vaihe? = Vaihe.OHJELMOINTI,
            version: Int? = 1,
            createdBy: String? = defaultUser,
            createdAt: ZonedDateTime? = DateFactory.getStartDatetime(),
            hankeStatus: HankeStatus = DRAFT,
        ): Hanke =
            Hanke(
                id,
                hankeTunnus,
                true,
                nimi,
                defaultKuvaus,
                vaihe,
                version,
                createdBy,
                createdAt,
                null,
                null,
                hankeStatus,
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
                    status = DRAFT,
                    hankeTunnus = defaultHankeTunnus,
                    nimi = defaultNimi,
                    kuvaus = defaultKuvaus,
                    vaihe = SUUNNITTELU,
                    onYKTHanke = true,
                    version = 0,
                    createdByUserId = defaultUser,
                    createdAt = DateFactory.getStartDatetime().toLocalDateTime(),
                    modifiedByUserId = defaultUser,
                    modifiedAt = DateFactory.getEndDatetime().toLocalDateTime(),
                    perustaja = defaultPerustaja.toEntity(),
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
                            hankeAttachmentEntity(hanke = this, createdByUser = defaultUser)
                        )
                    tormaystarkasteluTulokset = mutableListOf(tormaysTarkastelu(hankeEntity = this))
                }

        fun createRequest(
            nimi: String = defaultNimi,
            onYKTHanke: Boolean? = true,
            kuvaus: String? = defaultKuvaus,
            vaihe: Vaihe? = Vaihe.OHJELMOINTI,
            tyomaaKatuosoite: String? = null,
            tyomaaTyyppi: Set<TyomaaTyyppi>? = null,
        ): CreateHankeRequestBuilder =
            CreateHankeRequestBuilder(
                null,
                CreateHankeRequest(
                    nimi = nimi,
                    onYKTHanke = onYKTHanke,
                    kuvaus = kuvaus,
                    vaihe = vaihe,
                    tyomaaKatuosoite = tyomaaKatuosoite,
                    tyomaaTyyppi = tyomaaTyyppi
                )
            )

        /**
         * Add a hankealue with haitat to a test Hanke.
         *
         * Example:
         * ```
         * HankeFactory.create().withHankealue()
         * ```
         */
        fun Hanke.withHankealue(
            nimi: String? = null,
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
            perusIndeksi: Float = 1f,
            pyorailyIndeksi: Float = 1f,
            linjaautoIndeksi: Float = 1f,
            raitiovaunuIndeksi: Float = 1f,
        ): Hanke {
            this.tormaystarkasteluTulos =
                TormaystarkasteluTulos(
                    perusIndeksi,
                    pyorailyIndeksi,
                    linjaautoIndeksi,
                    raitiovaunuIndeksi,
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
                perus = 1.25f,
                pyoraily = 2.5f,
                linjaauto = 3.75f,
                raitiovaunu = 3.75f,
                hanke = hankeEntity,
            )
    }
}
