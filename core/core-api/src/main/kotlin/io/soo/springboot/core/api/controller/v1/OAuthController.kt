package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.response.LoginSuccessResponseDto
import io.soo.springboot.core.api.controller.v1.response.OAuth2UserInfoResponseDto
import io.soo.springboot.core.api.controller.v1.response.TokenInfoResponseDto
import io.soo.springboot.core.domain.OAuth2UserAttributeParser
import io.soo.springboot.core.domain.UserSessionMapService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/auth")
class OAuthController(
    private val sessionMapService: UserSessionMapService,
    private val oAuth2UserAttributeParser: OAuth2UserAttributeParser,
    private val authorizedClientService: OAuth2AuthorizedClientService,
) {

    @GetMapping("/{provider}/authorize-url")
    fun authorizeUrl(
        @PathVariable provider: String,
        @RequestParam(required = false) returnUrl: String?,
        @RequestHeader(name = "X-Device-Id", required = false) deviceId: String?,
        session: HttpSession,
    ): ApiResponse<String> {
        returnUrl?.let { check(it.startsWith("/")) { "returnUrl은 상대경로만 허용합니다." } }
        session.setAttribute("RETURN_URL", returnUrl)
        session.setAttribute("DEVICE_ID", deviceId)

        return ApiResponse.success("/oauth2/authorization/$provider")
    }

    @GetMapping("/login/success")
    fun loginSuccess(
        request: HttpServletRequest,
        authentication: Authentication?,
    ): ApiResponse<LoginSuccessResponseDto> {
        val session = request.getSession(false)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 존재하지 않습니다.")

        val token = authentication as? OAuth2AuthenticationToken
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증에 실패하였습니다.")

        val sessionId = session.id

        // ✅ DB에 저장한 세션-유저 매핑 확인
        val binding = sessionMapService.findActive(sessionId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 취소되었습니다.")

        // ✅ OAuth2 사용자 정보 파싱(카카오/네이버/구글)
        val provider = token.authorizedClientRegistrationId.lowercase()
        val attributes = token.principal.attributes

        val oauth2UserInfo = OAuth2UserInfoResponseDto(
            providerUserId = oAuth2UserAttributeParser.extractProviderUserId(provider, attributes),
            email = oAuth2UserAttributeParser.extractEmail(provider, attributes),
            nickname = oAuth2UserAttributeParser.extractNickname(provider, attributes),
            profileImageUrl = oAuth2UserAttributeParser.extractProfileImageUrl(provider, attributes),
        )

        // ✅ 토큰 메타 정보(토큰 값은 내려주지 않는 것을 권장)
        val client: OAuth2AuthorizedClient? =
            authorizedClientService.loadAuthorizedClient(token.authorizedClientRegistrationId, token.name)

        val tokenInfo = client?.let {
            TokenInfoResponseDto(
                tokenType = it.accessToken.tokenType.value,
                expiresAt = it.accessToken.expiresAt?.toString(),
                scopes = it.accessToken.scopes.toList(),
            )
        }

        val data = LoginSuccessResponseDto(
            sessionId = binding.sessionId,
            userId = binding.userId,
            deviceId = binding.deviceId,
            provider = binding.provider.name,
            createdAt = binding.createdAt,
            lastAccessedAt = binding.lastAccessedAt,
            oauth2 = oauth2UserInfo,
            token = tokenInfo,
        )

        return ApiResponse.success(data)
    }
}
