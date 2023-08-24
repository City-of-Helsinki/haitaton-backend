package fi.hel.haitaton.hanke.configuration

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
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
internal class OpenAPIConfiguration
