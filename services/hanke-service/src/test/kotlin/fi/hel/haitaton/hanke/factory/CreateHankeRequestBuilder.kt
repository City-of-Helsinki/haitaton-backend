package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.Hankealue

data class CreateHankeRequestBuilder(
    private val hankeService: HankeService?,
    private val request: CreateHankeRequest,
) {
    fun save(): Hanke = hankeService!!.createHanke(request)

    fun build(): CreateHankeRequest = request

    fun withRequest(f: CreateHankeRequest.() -> CreateHankeRequest) = copy(request = request.f())

    fun withYhteystiedot(): CreateHankeRequestBuilder = withRequest {
        copy(
            omistajat = listOf(HankeYhteystietoFactory.createDifferentiated(1, id = null)),
            rakennuttajat = listOf(HankeYhteystietoFactory.createDifferentiated(2, id = null)),
            toteuttajat = listOf(HankeYhteystietoFactory.createDifferentiated(3, id = null)),
            muut = listOf(HankeYhteystietoFactory.createDifferentiated(4, id = null)),
        )
    }

    fun withGeneratedOmistaja(i: Int = 1) = withGeneratedOmistajat(i)

    fun withGeneratedOmistajat(vararg discriminators: Int) = withRequest {
        copy(
            omistajat =
                HankeYhteystietoFactory.createDifferentiated(discriminators.toList()) { id = null }
        )
    }

    fun withGeneratedRakennuttaja(i: Int = 1) = withRequest {
        copy(rakennuttajat = listOf(HankeYhteystietoFactory.createDifferentiated(i, id = null)))
    }

    fun withHankealue(alue: Hankealue = HankealueFactory.create(id = null, hankeId = null)) =
        withRequest {
            copy(
                alueet = (this.alueet ?: listOf()) + alue,
                tyomaaKatuosoite = "Testikatu 1",
                tyomaaTyyppi = setOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
            )
        }
}
