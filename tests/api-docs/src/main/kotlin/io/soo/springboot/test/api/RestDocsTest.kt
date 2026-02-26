package io.soo.springboot.test.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.restassured.module.mockmvc.RestAssuredMockMvc
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import jakarta.servlet.Filter
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

@Tag("restdocs")
@ExtendWith(RestDocumentationExtension::class)
abstract class RestDocsTest {
    lateinit var mockMvc: MockMvcRequestSpecification
    private lateinit var restDocumentation: RestDocumentationContextProvider

    @BeforeEach
    fun setUp(restDocumentation: RestDocumentationContextProvider) {
        this.restDocumentation = restDocumentation
    }

    protected fun given(): MockMvcRequestSpecification {
        return mockMvc
    }

    protected fun mockController(controller: Any, vararg filters: Filter): MockMvcRequestSpecification {
        val mockMvc = createMockMvc(controller, *filters)
        return RestAssuredMockMvc.given()
            .mockMvc(mockMvc)
    }

    private fun createMockMvc(controller: Any, vararg filters: Filter): MockMvc {
        val converter = MappingJackson2HttpMessageConverter(objectMapper())
        val base: StandaloneMockMvcBuilder =
            MockMvcBuilders.standaloneSetup(controller)
        val builder1: StandaloneMockMvcBuilder =
            base.apply(MockMvcRestDocumentation.documentationConfiguration(restDocumentation))
        val builder2: StandaloneMockMvcBuilder =
            builder1.setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        val builder3: StandaloneMockMvcBuilder =
            if (filters.isNotEmpty()) builder2.addFilters(*filters) else builder2
        return builder3
            .setMessageConverters(converter)
            .build()
    }

    private fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
    }
}
