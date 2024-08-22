package fi.hel.haitaton.hanke.banners

typealias BannersResponse = Map<BannerType, BannerResponse>

data class BannerResponse(
    val label: LocalizedText,
    val text: LocalizedText,
)

data class LocalizedText(
    val fi: String,
    val sv: String,
    val en: String,
)
