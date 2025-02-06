package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory.DEFAULT_HHS
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

class HakemusalueTest {

    @Nested
    inner class ListChanges {

        val path = "areas[0]"

        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        @Nested
        inner class Johtoselvitys {

            val base = ApplicationFactory.createCableReportApplicationArea()

            @Test
            fun `returns no changes when areas are identical`() {
                val updated = base.copy()

                val result = base.listChanges(path, updated)

                assertThat(result).isEmpty()
            }

            @Test
            fun `returns a change when other area is null`() {
                val result = base.listChanges(path, null)

                assertThat(result).containsExactly(path)
            }

            @Test
            fun `returns a change when name differs`() {
                val updated = base.copy(name = "Uusi nimi")

                val result = base.listChanges(path, updated)

                assertThat(result).containsExactly(path)
            }

            @Test
            fun `returns a change when geometry differs`() {
                val updated = base.copy(geometry = GeometriaFactory.thirdPolygon())

                val result = base.listChanges(path, updated)

                assertThat(result).containsExactly(path)
            }
        }

        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        @Nested
        inner class Kaivuilmoitus {

            val base = ApplicationFactory.createExcavationNotificationArea()

            @Test
            fun `returns no changes when areas are identical`() {
                val updated = base.copy()

                val result = base.listChanges(path, updated)

                assertThat(result).isEmpty()
            }

            @Test
            fun `returns a change when other area is null`() {
                val result = base.listChanges(path, null)

                assertThat(result).containsExactly(path)
            }

            @Test
            fun `returns all changes in area`() {
                val updated =
                    ApplicationFactory.createExcavationNotificationArea(
                        name = "Uusi nimi",
                        katuosoite = "Uusi katuosoite",
                        tyonTarkoitukset = setOf(TyomaaTyyppi.VIEMARI),
                        meluhaitta = Meluhaitta.SATUNNAINEN_MELUHAITTA,
                        polyhaitta = Polyhaitta.JATKUVA_POLYHAITTA,
                        tarinahaitta = Tarinahaitta.TOISTUVA_TARINAHAITTA,
                        kaistahaitta =
                            VaikutusAutoliikenteenKaistamaariin
                                .YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA,
                        kaistahaittojenPituus =
                            AutoliikenteenKaistavaikutustenPituus.PITUUS_100_499_METRIA,
                        lisatiedot = "Täydennetyt lisätiedot",
                        haittojenhallintasuunnitelma =
                            DEFAULT_HHS.toMutableMap().mapValues { it.value + " täydennetty" },
                    )

                val result = base.listChanges(path, updated)

                assertThat(result)
                    .containsExactly(
                        path,
                        "$path.name",
                        "$path.katuosoite",
                        "$path.tyonTarkoitukset",
                        "$path.meluhaitta",
                        "$path.polyhaitta",
                        "$path.tarinahaitta",
                        "$path.kaistahaitta",
                        "$path.kaistahaittojenPituus",
                        "$path.lisatiedot",
                        "$path.haittojenhallintasuunnitelma[YLEINEN]",
                        "$path.haittojenhallintasuunnitelma[PYORALIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[AUTOLIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[RAITIOLIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[LINJAAUTOLIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[MUUT]",
                    )
            }

            @Test
            fun `returns changes in haittojenhallintasuunnitelma`() {
                val updated =
                    ApplicationFactory.createExcavationNotificationArea(
                        haittojenhallintasuunnitelma =
                            DEFAULT_HHS.toMutableMap().mapValues { it.value + " täydennetty" }
                    )

                val result = base.listChanges(path, updated)

                assertThat(result)
                    .containsExactly(
                        "$path.haittojenhallintasuunnitelma[YLEINEN]",
                        "$path.haittojenhallintasuunnitelma[PYORALIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[AUTOLIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[RAITIOLIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[LINJAAUTOLIIKENNE]",
                        "$path.haittojenhallintasuunnitelma[MUUT]",
                    )
            }

            @Nested
            inner class WorkAreaChanges {

                private val areaWithoutWorkAreas = base.copy(tyoalueet = emptyList())

                private val areaWithTwoWorkAreas =
                    base.copy(
                        tyoalueet =
                            listOf(
                                ApplicationFactory.createTyoalue(
                                    geometry = GeometriaFactory.polygon()
                                ),
                                ApplicationFactory.createTyoalue(
                                    geometry = GeometriaFactory.secondPolygon()
                                ),
                            )
                    )

                @Test
                fun `returns a change when work areas were empty but new ones are added`() {
                    val result = areaWithoutWorkAreas.listChanges(path, areaWithTwoWorkAreas)

                    assertThat(result)
                        .containsExactly(path, "$path.tyoalueet[0]", "$path.tyoalueet[1]")
                }

                @Test
                fun `returns a change when there were work areas but now they are empty`() {
                    val result = areaWithTwoWorkAreas.listChanges(path, areaWithoutWorkAreas)

                    assertThat(result)
                        .containsExactly(path, "$path.tyoalueet[0]", "$path.tyoalueet[1]")
                }

                @Test
                fun `returns several changes when a work area is removed from the middle`() {
                    val tyoalueet =
                        listOf(
                            ApplicationFactory.createTyoalue(geometry = GeometriaFactory.polygon()),
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.thirdPolygon()
                            ),
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.thirdPolygon()
                            ),
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.secondPolygon()
                            ),
                        )
                    val base = base.copy(tyoalueet = tyoalueet)

                    val result = base.listChanges(path, areaWithTwoWorkAreas)

                    assertThat(result)
                        .containsExactly(
                            path,
                            "$path.tyoalueet[1]",
                            "$path.tyoalueet[1].geometry",
                            "$path.tyoalueet[2]",
                            "$path.tyoalueet[3]",
                        )
                }

                @Test
                fun `returns several changes when a work area is added to the middle`() {
                    val tyoalueet =
                        listOf(
                            ApplicationFactory.createTyoalue(geometry = GeometriaFactory.polygon()),
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.thirdPolygon()
                            ),
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.thirdPolygon()
                            ),
                            ApplicationFactory.createTyoalue(
                                geometry = GeometriaFactory.secondPolygon()
                            ),
                        )
                    val updated = base.copy(tyoalueet = tyoalueet)

                    val result = areaWithTwoWorkAreas.listChanges(path, updated)

                    assertThat(result)
                        .containsExactly(
                            path,
                            "$path.tyoalueet[1]",
                            "$path.tyoalueet[1].geometry",
                            "$path.tyoalueet[2]",
                            "$path.tyoalueet[3]",
                        )
                }

                @Test
                fun `returns a change in tyoalue geometry`() {
                    val tyoalueet =
                        listOf(
                            ApplicationFactory.createTyoalue()
                                .copy(geometry = GeometriaFactory.polygon())
                        )
                    val updated = base.copy(tyoalueet = tyoalueet)

                    val result = base.listChanges(path, updated)

                    assertThat(result)
                        .containsExactly(path, "$path.tyoalueet[0]", "$path.tyoalueet[0].geometry")
                }
            }
        }
    }
}
