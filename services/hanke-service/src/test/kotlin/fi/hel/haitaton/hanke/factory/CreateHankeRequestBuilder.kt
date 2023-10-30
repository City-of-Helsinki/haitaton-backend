package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.NewGeometriat
import fi.hel.haitaton.hanke.domain.NewHankealue
import fi.hel.haitaton.hanke.domain.NewYhteystieto

data class CreateHankeRequestBuilder(
    private val hankeService: HankeService?,
    private val request: CreateHankeRequest,
) {
    fun save(): Hanke = hankeService!!.createHanke(request)

    fun build(): CreateHankeRequest = request

    fun withRequest(f: CreateHankeRequest.() -> CreateHankeRequest) = copy(request = request.f())

    fun withYhteystiedot(): CreateHankeRequestBuilder = withRequest {
        copy(
            omistajat = listOf(HankeYhteystietoFactory.createDifferentiated(1).toCreateRequest()),
            rakennuttajat =
                listOf(HankeYhteystietoFactory.createDifferentiated(2).toCreateRequest()),
            toteuttajat = listOf(HankeYhteystietoFactory.createDifferentiated(3).toCreateRequest()),
            muut = listOf(HankeYhteystietoFactory.createDifferentiated(4).toCreateRequest()),
        )
    }

    fun withGeneratedOmistaja(i: Int = 1) = withGeneratedOmistajat(i)

    fun withGeneratedOmistajat(vararg discriminators: Int) = withRequest {
        copy(
            omistajat =
                HankeYhteystietoFactory.createDifferentiated(discriminators.toList()).map {
                    it.toCreateRequest()
                }
        )
    }

    fun withGeneratedRakennuttaja(i: Int = 1) = withRequest {
        copy(
            rakennuttajat =
                listOf(HankeYhteystietoFactory.createDifferentiated(i, id = null).toCreateRequest())
        )
    }

    fun withHankealue(alue: Hankealue = HankealueFactory.create()) = withRequest {
        copy(
            alueet = (this.alueet ?: listOf()) + alue.toCreateRequest(),
            tyomaaKatuosoite = "Testikatu 1",
            tyomaaTyyppi = setOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        )
    }
}

fun Hankealue.toCreateRequest() =
    NewHankealue(
        haittaAlkuPvm,
        haittaLoppuPvm,
        geometriat?.let { NewGeometriat(it.featureCollection) },
        kaistaHaitta,
        kaistaPituusHaitta,
        meluHaitta,
        polyHaitta,
        tarinaHaitta,
        nimi
    )

fun HankeYhteystieto.toCreateRequest() =
    NewYhteystieto(
        nimi,
        email,
        alikontaktit,
        puhelinnumero,
        organisaatioNimi,
        osasto,
        rooli,
        tyyppi,
        ytunnus
    )

fun NewYhteystieto.toHankeYhteystieto() =
    HankeYhteystieto(
        id,
        nimi,
        email,
        alikontaktit,
        puhelinnumero,
        organisaatioNimi,
        osasto,
        rooli,
        tyyppi,
        ytunnus
    )
