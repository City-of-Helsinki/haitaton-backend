package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.NewGeometriat
import fi.hel.haitaton.hanke.geometria.Geometriat

object GeometriaFactory {

    fun create(): Geometriat =
        "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()

    fun createNew(): NewGeometriat = NewGeometriat(create().featureCollection)
}
