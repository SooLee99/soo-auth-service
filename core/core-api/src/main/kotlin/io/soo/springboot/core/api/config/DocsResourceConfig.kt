package io.soo.springboot.core.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class DocsResourceConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // http://localhost:8080/docs/swagger/index.html
        registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/static/docs/")
    }
}