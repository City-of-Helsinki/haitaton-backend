package fi.hel.haitaton.hanke.banners

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/banners")
class BannerController(private val bannerService: BannerService) {
    @GetMapping
    @Operation(
        summary = "Get current notification banners",
        description = "Get a list of banners the UI should display to the users.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(description = "Internal server error", responseCode = "500")])
    fun listBanners(): BannersResponse = bannerService.listBanners()
}
