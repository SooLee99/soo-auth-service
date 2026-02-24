package io.soo.springboot.core.api.security.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.controller.v1.response.LoginSuccessResponse
import io.soo.springboot.core.domain.UserIdResolver
import io.soo.springboot.core.domain.token.JsonWebTokenService
import io.soo.springboot.core.domain.token.UserPrincipal
import io.soo.springboot.core.domain.token.UserPrincipalLoader
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val objectMapper: ObjectMapper,
    private val jwtService: JsonWebTokenService,
    private val userIdResolver: UserIdResolver,
    private val userPrincipalLoader: UserPrincipalLoader,
) : AuthenticationSuccessHandler {

    companion object {
        private const val ATTR_DEVICE_ID = "DEVICE_ID"
        private const val MAX_DEVICE_ID = 255
        private fun normDeviceId(s: String) = s.trim().take(MAX_DEVICE_ID)
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
//        // 1) deviceId: OAuth2 콜백에는 헤더가 못 오므로 세션에서 꺼내는 게 정석
//        val deviceId = run {
//            val fromHeader = request.getHeader("X-Device-Id")
//            val fromSession = try {
//                request.getSession(false)?.getAttribute(ATTR_DEVICE_ID) as? String
//            } catch (_: Exception) {
//                null
//            }
//            normDeviceId(fromHeader ?: fromSession ?: "")
//        }
//
//        if (deviceId.isBlank()) {
//            writeJson(
//                response,
//                HttpServletResponse.SC_BAD_REQUEST,
//                ApiResponse.error(
//                    type = ErrorType.INVALID_REQUEST,
//                    message = "deviceId가 없습니다. /api/v1/auth/oauth2/{provider}/authorize-url 를 통해 시작하거나 deviceId를 세션에 저장해 주세요.",
//                    req = request,
//                    fields = mapOf("deviceId" to "missing"),
//                )
//            )
//            return
//        }

        // 2) userId
        val userId = userIdResolver.resolve(authentication)

        // 3) 앱의 권한/역할을 포함한 principal 로드 (OAuth2AuthenticationToken의 authorities 쓰면 앱 롤이 아닐 가능성 큼)
        val principal = (userPrincipalLoader.loadByUserId(userId) as? UserPrincipal)
        if (principal == null) {
            writeJson(
                response,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                ApiResponse.error(
                    type = ErrorType.DEFAULT_ERROR,
                    message = "UserPrincipal을 로드할 수 없습니다.",
                    req = request,
                )
            )
            return
        }

        val provider: AuthProvider = principal.provider ?: AuthProvider.LOCAL

        // 4) JWT 발급은 “앱 principal 기반 Authentication”으로 (roles claim 일관성)
        val appAuth = UsernamePasswordAuthenticationToken(principal.username, null, principal.authorities)
        val issued = jwtService.issue(appAuth, userId, "", provider)

        val roles = principal.authorities
            .map { it.authority }
            .distinct()
            .sorted()

        val payload = LoginSuccessResponse.Data(
            token = LoginSuccessResponse.Token(
                accessToken = issued.accessToken,
                expiresIn = issued.accessExpiresInSec,
                refreshToken = issued.refreshToken,
                refreshExpiresIn = issued.refreshExpiresInSec,
            ),
            user = LoginSuccessResponse.User(
                id = userId,
                provider = provider.name,
                email = principal.email ?: principal.username,
                roles = roles,
            )
        )

//        // (선택) 세션에 저장한 값 정리
//        request.getSession(false)?.removeAttribute(ATTR_DEVICE_ID)

        writeJson(
            response,
            HttpServletResponse.SC_OK,
            ApiResponse.success(req = request, data = payload)
        )
    }

    private fun writeJson(response: HttpServletResponse, status: Int, body: Any) {
        response.status = status
        response.characterEncoding = "UTF-8"
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.use { it.write(objectMapper.writeValueAsString(body)) }
    }
}
