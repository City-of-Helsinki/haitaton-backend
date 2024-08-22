package fi.hel.haitaton.hanke.banners

object BannerFactory {
    fun createEntity(type: BannerType): BannerEntity =
        BannerEntity(
            type,
            "Finnish $type body",
            "Swedish $type body",
            "English $type body",
            "Finnish $type label",
            "Swedish $type label",
            "English $type label",
        )

    fun createResponseMap(vararg types: BannerType): BannersResponse =
        types.associateWith { createResponse(it) }

    fun createResponse(type: BannerType): BannerResponse {
        val entity = createEntity(type)
        return BannerResponse(
            label = LocalizedText(entity.labelFi, entity.labelSv, entity.labelEn),
            text = LocalizedText(entity.textFi, entity.textSv, entity.textEn),
        )
    }
}
