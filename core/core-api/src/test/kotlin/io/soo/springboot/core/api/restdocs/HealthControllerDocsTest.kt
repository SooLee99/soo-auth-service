package io.soo.springboot.core.api.restdocs

import io.soo.springboot.core.api.controller.v1.HealthController
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HealthControllerDocsTest : RestDocsTest() {

    @BeforeEach
    fun init() {
        mockMvc = mockController(HealthController())
    }

    @Test
    fun health() {
        given()
            .`when`()
            .get("/health")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "health",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                )
            )
    }
}