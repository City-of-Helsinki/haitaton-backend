package fi.hel.haitaton.hanke.banners

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "ui_notification_banner")
class BannerEntity(
    @Enumerated(EnumType.STRING) @Id val id: BannerType,
    @Column(name = "text_fi") val textFi: String,
    @Column(name = "text_sv") val textSv: String,
    @Column(name = "text_en") val textEn: String,
    @Column(name = "label_fi") val labelFi: String,
    @Column(name = "label_sv") val labelSv: String,
    @Column(name = "label_en") val labelEn: String,
)
