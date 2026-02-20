package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.domain.LocalAccountService
import io.soo.springboot.core.domain.LocalSignUpCommand
import io.soo.springboot.core.domain.token.JsonWebTokenService
import io.soo.springboot.core.domain.token.RotateResult
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth/local")
class LocalAccountController(
    private val localAccountService: LocalAccountService,
    private val jsonWebTokenService: JsonWebTokenService,
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

    @PostMapping("/token/refresh", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody req: RefreshRequest,
    ): Map<String, Any?> {
        val (status, tokens) = jsonWebTokenService.refresh(req.refreshToken, deviceId)

        return when (status) {
            is RotateResult.Success -> mapOf("result" to "OK", "data" to tokens)
            RotateResult.NotFoundOrExpired -> mapOf("result" to "ERROR", "error" to mapOf("code" to "INVALID_REFRESH"))
            RotateResult.AlreadyUsed -> mapOf("result" to "ERROR", "error" to mapOf("code" to "REUSED_REFRESH"))
            RotateResult.DeviceMismatch -> mapOf("result" to "ERROR", "error" to mapOf("code" to "DEVICE_MISMATCH"))
        }
    }

    @PostMapping("/logout", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun logout(
        @RequestHeader("X-Device-Id") deviceId: String,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody(required = false) body: LogoutRequest?,
    ): Map<String, Any> {
        localAccountService.logout(
            jwt = jwt,
            deviceId = deviceId,
            refreshToken = body?.refreshToken,
            logoutAll = body?.logoutAll ?: false,
        )
        return mapOf("result" to "OK")
    }
}
