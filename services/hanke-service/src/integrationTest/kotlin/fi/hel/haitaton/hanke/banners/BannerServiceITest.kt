package fi.hel.haitaton.hanke.banners

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.key
import assertk.assertions.prop
import fi.hel.haitaton.hanke.IntegrationTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BannerServiceITest(
    @Autowired private val bannerService: BannerService,
    @Autowired private val bannerRepository: BannerRepository,
) : IntegrationTest() {

    @Nested
    inner class ListBanners {
        @Test
        fun `returns empty map when there are no banners`() {
            val result = bannerService.listBanners()

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns banners when there are some`() {
            bannerRepository.saveAll(
                listOf(
                    BannerFactory.createEntity(BannerType.INFO),
                    BannerFactory.createEntity(BannerType.WARNING),
                    BannerFactory.createEntity(BannerType.ERROR),
                ))

            val result = bannerService.listBanners()

            assertThat(result)
                .transform { it.keys }
                .containsOnly(BannerType.INFO, BannerType.WARNING, BannerType.ERROR)
        }

        @Test
        fun `returns the correct texts`() {
            bannerRepository.save(BannerFactory.createEntity(BannerType.WARNING))

            val result = bannerService.listBanners()

            assertThat(result).key(BannerType.WARNING).all {
                prop(BannerResponse::label).all {
                    prop(LocalizedText::fi).isEqualTo("Finnish WARNING label")
                    prop(LocalizedText::sv).isEqualTo("Swedish WARNING label")
                    prop(LocalizedText::en).isEqualTo("English WARNING label")
                }
                prop(BannerResponse::text).all {
                    prop(LocalizedText::fi).isEqualTo("Finnish WARNING body")
                    prop(LocalizedText::sv).isEqualTo("Swedish WARNING body")
                    prop(LocalizedText::en).isEqualTo("English WARNING body")
                }
            }
        }
    }
}
