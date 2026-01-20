package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.JwtLogoutRequest
import io.soo.springboot.core.api.controller.v1.request.SignUpRequest
import io.soo.springboot.core.api.controller.v1.request.WithdrawRequest
import io.soo.springboot.core.api.controller.v1.response.SignUpResult
import io.soo.springboot.core.domain.JwtDenylistService
import io.soo.springboot.core.domain.LocalAuthService
import io.soo.springboot.core.domain.UserSessionMapService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.apache.tomcat.util.net.openssl.ciphers.Authentication
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/auth")
class LocalAuthController(
    private val localAuthService: LocalAuthService,
    private val denylist: JwtDenylistService,
) {

    /**
     * ✅ 회원가입
     * POST /api/v1/auth/signup
     */
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid req: SignUpRequest): ApiResponse<SignUpResult> {
        val result = localAuthService.signUp(
            email = req.email,
            rawPassword = req.password,
            name = req.name,
            nickname = req.nickname,
            profileImageUrl = req.profileImageUrl,
            thumbnailImageUrl = req.thumbnailImageUrl,
            birthyear = req.birthyear,
            birthday = req.birthday,
        )
        return ApiResponse.success(result)
    }

    /**
     * ✅ JWT 로그아웃 = 현재 토큰을 denylist(블랙리스트)에 등록
     * POST /api/v1/auth/jwt/logout
     *
     * - 인증된 요청이어야 함(Bearer 토큰 필요)
     */
    @PostMapping("/jwt/logout")
    fun logout(
        auth: JwtAuthenticationToken,
        @RequestBody(required = false) req: JwtLogoutRequest?,
    ): ApiResponse<Unit> {
        val jwt = auth.token
        val tokenValue = jwt.tokenValue
        val jti = jwt.id
        val expiresAt = jwt.expiresAt

        denylist.revoke(
            tokenValue = tokenValue,
            jti = jti,
            expiresAt = expiresAt,
            reason = req?.reason,
        )

        return ApiResponse.success(Unit)
    }

    @PostMapping("/withdraw")
    fun withdraw(
        request: HttpServletRequest,
        auth: Authentication?,
        @RequestBody(required = false) @Valid req: WithdrawRequest?,
    ): ApiResponse<Unit> {
        val session = request.getSession(false)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No session")

        if (auth == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")
        }

        localAuthService.withdrawBySession(
            sessionId = session.id,
            reason = req?.reason,
            passwordForLocal = req?.password,
        )

        return ApiResponse.success(Unit)
    }
}
