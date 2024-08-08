package fi.hel.haitaton.hanke.banners

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository interface BannerRepository : JpaRepository<BannerEntity, BannerType> {}
