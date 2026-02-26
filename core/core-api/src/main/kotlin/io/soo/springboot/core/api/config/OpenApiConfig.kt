package io.soo.springboot.core.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger springdoc-ui 구성 파일
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        val info = Info()
            .title("Auth Service API Document")
            .version("v0.0.1")
            .description("API 명세서입니다.")

        return OpenAPI()
            .components(Components())
            .addServersItem(Server().url("/"))
            .info(info)
    }
}