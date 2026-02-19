package io.soo.springboot.core.api.controller.v1

import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.domain.LocalAccountService
import io.soo.springboot.core.domain.LocalSignUpCommand
import io.soo.springboot.core.domain.token.JwtService
import io.soo.springboot.core.domain.token.RotateResult


@RestController
@RequestMapping("/api/v1/auth/local/")
class LocalAccountController(
    private val localAccountService: LocalAccountService,
    private val jwtService: JwtService,
) {
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: SignUpRequest) {
        localAccountService.signUp(
            LocalSignUpCommand(
                email = request.email,
                password = request.password,
                phoneNumber = request.phoneNumber,
                gender = request.gender,
                locale = request.locale,
                nickname = request.nickname,
                name = request.name,
                profileImageUrl = request.profileImageUrl,
                thumbnailImageUrl = request.thumbnailImageUrl,
                birthyear = request.birthyear,
                birthday = request.birthday,
            )
        )
    }

    @PostMapping("/token/refresh")
    fun refresh(@RequestBody req: RefreshRequest): Map<String, Any?> {
        val (status, tokens) = jwtService.refresh(req.refreshToken)
        return when (status) {
            is RotateResult.Success -> mapOf("result" to "OK", "data" to tokens)
            RotateResult.NotFoundOrExpired -> mapOf("result" to "ERROR", "error" to mapOf("code" to "INVALID_REFRESH"))
            RotateResult.AlreadyUsed -> mapOf("result" to "ERROR", "error" to mapOf("code" to "REUSED_REFRESH"))
        }
    }

    @PostMapping("/logout", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun logout(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody(required = false) body: LogoutRequest?,
    ): Map<String, Any> {
        localAccountService.logout(jwt, body?.refreshToken ?: "", body?.logoutAll ?: false)
        return mapOf("result" to "OK")
    }
}
