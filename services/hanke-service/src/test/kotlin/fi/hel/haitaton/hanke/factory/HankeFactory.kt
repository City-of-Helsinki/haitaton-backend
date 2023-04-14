package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.Perustaja
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HankeFactory(private val hankeService: HankeService) {

    /**
     * Create a new hanke and save it to database.
     *
     * Needs a mock user set up, since `hankeService.createHanke` calls currentUserId() directly.
     */
    fun save(
        nimi: String? = defaultNimi,
        alkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
        loppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
        vaihe: Vaihe? = Vaihe.OHJELMOINTI,
        suunnitteluVaihe: SuunnitteluVaihe? = null,
    ) =
        hankeService.createHanke(
            create(
                nimi = nimi,
                alkuPvm = alkuPvm,
                loppuPvm = loppuPvm,
                vaihe = vaihe,
                suunnitteluVaihe = suunnitteluVaihe,
            )
        )

    fun save(hanke: Hanke) = hankeService.createHanke(hanke)

    companion object {

        const val defaultHankeTunnus = "HAI21-1"
        const val defaultNimi = "Hämeentien perusparannus ja katuvalot"
        const val defaultId = 123
        const val defaultUser = "Risto"

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
            id: Int? = defaultId,
            hankeTunnus: String? = defaultHankeTunnus,
            nimi: String? = defaultNimi,
            alkuPvm: ZonedDateTime? = DateFactory.getStartDatetime(),
            loppuPvm: ZonedDateTime? = DateFactory.getEndDatetime(),
            vaihe: Vaihe? = Vaihe.OHJELMOINTI,
            suunnitteluVaihe: SuunnitteluVaihe? = null,
            version: Int? = 1,
            createdBy: String? = defaultUser,
            createdAt: ZonedDateTime? = DateFactory.getStartDatetime(),
            hankeStatus: HankeStatus = HankeStatus.DRAFT,
        ): Hanke =
            Hanke(
                id,
                hankeTunnus,
                true,
                nimi,
                "lorem ipsum dolor sit amet...",
                alkuPvm,
                loppuPvm,
                vaihe,
                suunnitteluVaihe,
                version,
                createdBy,
                createdAt,
                null,
                null,
                hankeStatus,
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
            alue: Hankealue =
                HankealueFactory.create(
                    hankeId = this.id,
                    haittaAlkuPvm = this.alkuPvm,
                    haittaLoppuPvm = this.loppuPvm
                )
        ): Hanke {
            this.tyomaaKatuosoite = "Testikatu 1"
            this.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
            this.tyomaaTyyppi.add(TyomaaTyyppi.MUU)

            this.alueet.add(alue)

            return this
        }

        fun Hanke.withTormaystarkasteluTulos(
            perusIndeksi: Float = 1f,
            pyorailyIndeksi: Float = 1f,
            joukkoliikenneIndeksi: Float = 1f,
        ): Hanke {
            this.tormaystarkasteluTulos =
                TormaystarkasteluTulos(perusIndeksi, pyorailyIndeksi, joukkoliikenneIndeksi)
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
            mutator: (HankeYhteystieto) -> Unit = {},
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

        fun Hanke.withPerustaja(
            newPerustaja: Perustaja? = Perustaja("Pertti Perustaja", "foo@bar.com")
        ): Hanke = apply { perustaja = newPerustaja }

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
            mutator: (HankeYhteystieto) -> Unit = {}
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
            mutator: (HankeYhteystieto) -> Unit = {}
        ): Hanke = withGeneratedOmistajat(ids.toList(), mutator)

        /**
         * Same as [Hanke.withGeneratedOmistajat] but adds a single omistaja.
         *
         * Example:
         * ```
         * HankeFactory.create().withGeneratedOmistaja(1)
         * ```
         */
        fun Hanke.withGeneratedOmistaja(id: Int, mutator: (HankeYhteystieto) -> Unit = {}): Hanke =
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
            mutator: (HankeYhteystieto) -> Unit = {}
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
            mutator: (HankeYhteystieto) -> Unit = {}
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
            mutator: (HankeYhteystieto) -> Unit = {}
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
            mutator: (HankeYhteystieto) -> Unit = {}
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
            mutator: (HankeYhteystieto) -> Unit = {}
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
            mutator: (HankeYhteystieto) -> Unit = {}
        ): Hanke = withGeneratedToteuttajat(listOf(id), mutator)
    }
}
