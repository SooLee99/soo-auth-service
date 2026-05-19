package io.soo.springboot.core.api.controller.v1.auth.oauth2

import jakarta.validation.constraints.NotBlank

data class KakaoSdkLoginRequest(
    @field:NotBlank
    val kakaoAccessToken: String,
)
