package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.RefreshRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.response.LogoutRequest
import io.soo.springboot.core.domain.*
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/v1/auth")
class LocalAccountController(
    private val localAccountService: LocalAccountService,
    private val jwtService: JwtService,
    private val denylistStore: JwtDenylistStore,
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

        // 1) access token denylist(jti)
        val jti = jwt.id
        val exp = jwt.expiresAt

        // 2) expiration time check
        if (!jti.isNullOrBlank() && exp != null) {
            val ttl = Duration.between(Instant.now(), exp).coerceAtLeast(Duration.ZERO)
            if (!ttl.isZero) denylistStore.deny(jti, ttl)
        }

        // 3) refresh token revoke
        val refreshToken = body?.refreshToken
        if (!refreshToken.isNullOrBlank()) {
            jwtService.revoke(refreshToken)
        }

        // 4) 전체 로그아웃(옵션): 토큰에 uid claim이 있어야 함
        if (body?.logoutAll == true) {
            val uid = (jwt.claims["uid"] as? Number)?.toLong()
            if (uid != null) jwtService.revokeAll(uid)
        }

        return mapOf("result" to "OK")
    }
}
