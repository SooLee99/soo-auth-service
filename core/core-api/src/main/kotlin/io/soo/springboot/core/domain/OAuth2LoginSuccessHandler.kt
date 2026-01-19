package io.soo.springboot.core.domain

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.stereotype.Component

import io.soo.springboot.core.enums.AuthProvider


@Component
class OAuth2LoginSuccessHandler(
    private val userAccountService: UserAccountService,
    private val loginAttemptService: LoginAttemptService,
    private val userDeviceService: UserDeviceService,
    private val sessionMapService: UserSessionMapService,
    private val deviceBlockService: DeviceBlockService,
    private val sessionRepository: SessionRepository<out Session>,

) : AuthenticationSuccessHandler {
    private val log = LoggerFactory.getLogger(javaClass)
    companion object {
        const val SESSION_DEVICE_ID_KEY = "DEVICE_ID"
        const val SESSION_RETURN_URL_KEY = "RETURN_URL"
        const val HEADER_DEVICE_ID = "X-Device-Id"
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val token = authentication as OAuth2AuthenticationToken

        // registrationId는 String ("kakao"/"naver"/"google")
        val registrationId = token.authorizedClientRegistrationId

        val provider: AuthProvider = when (registrationId.lowercase()) {
            "kakao" -> AuthProvider.KAKAO
            "naver" -> AuthProvider.NAVER
            "google" -> AuthProvider.GOOGLE
            else -> error("Unsupported provider: $registrationId")
        }

        val session = request.getSession(false)
        val sessionId = session?.id

        val deviceId = (
            (session?.getAttribute(SESSION_DEVICE_ID_KEY) as? String)
                ?: request.getHeader(HEADER_DEVICE_ID)
            )?.takeIf { it.isNotBlank() }

        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")

        // 1) provider 사용자 → 우리 userId 업서트
        val (userId, providerUserId) = userAccountService.upsertFromOAuth2(token, request)

        // 2) 디바이스 차단 체크
        if (deviceBlockService.isBlocked(userId, deviceId)) {
            // 세션 강제 제거(즉시 로그아웃)
            sessionId?.let { sessionRepository.deleteById(it) }

            loginAttemptService.record(
                success = false,
                provider = provider,
                userId = userId,
                providerUserId = providerUserId,
                deviceId = deviceId,
                ip = ip,
                userAgent = ua,
                errorCode = "DEVICE_BLOCKED",
                errorMessage = "Blocked device",
            )

            response.sendError(403, "Blocked device")
            return
        }

        // 3) 성공 로그 기록
        val logitem = loginAttemptService.record(
            success = true,
            provider = provider,
            userId = userId,
            providerUserId = providerUserId,
            deviceId = deviceId,
            ip = ip,
            userAgent = ua,
            errorCode = null,
            errorMessage = null,
        )
        log.info("로그인이 완료되었습니다. -> $logitem")

        // 4) 디바이스 upsert(없으면 unknown 처리하는 로직을 서비스 안에 넣어두는 게 좋음)
        userDeviceService.upsertLogin(userId, deviceId, ip, ua)

        // 5) 세션 매핑 저장 (강제 로그아웃 핵심)
        if (sessionId != null) {
            val deviceIdForMap = deviceId ?: UserDeviceService.UNKNOWN_DEVICE
            sessionMapService.bind(sessionId, userId, deviceIdForMap, provider)
        }

        // 6) (선택) OAuth2 토큰 접근(필요시 저장)
//        val client: OAuth2AuthorizedClient? =
//            authorizedClientService.loadAuthorizedClient(registrationId, token.name)
//        client?.accessToken?.tokenValue
//        client?.refreshToken?.tokenValue
//        // 저장 시: 반드시 암호화/수명/유출 대응 정책 필요

        // 7) 리다이렉트(또는 JSON 응답)
        val returnUrl = (session?.getAttribute(SESSION_RETURN_URL_KEY) as? String) ?: "/api/v1/auth/login/success"
        session?.removeAttribute(SESSION_RETURN_URL_KEY)
        session?.removeAttribute(SESSION_DEVICE_ID_KEY)
        response.sendRedirect(returnUrl)
    }
}
