package fi.hel.haitaton.hanke.banners

import org.springframework.stereotype.Service

@Service
class BannerService(private val bannerRepository: BannerRepository) {

    fun listBanners(): BannersResponse =
        bannerRepository
            .findAll()
            .associateBy { it.id }
            .mapValues { (_, v) ->
                BannerResponse(
                    label = LocalizedText(v.labelFi, v.labelSv, v.labelEn),
                    text = LocalizedText(v.textFi, v.textSv, v.textEn),
                )
            }
}
