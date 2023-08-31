package fi.hel.haitaton.hanke.configuration

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server

@OpenAPIDefinition(
    info =
        Info(
            title = "Haitaton Internal API",
            description = "API for Haitaton internal use. Can change without warning.",
            version = "1"
        ),
    servers = [Server(url = "/api/")],
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer",
)
internal class OpenAPIConfiguration
