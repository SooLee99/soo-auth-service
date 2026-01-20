package io.soo.springboot.core.domain

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime

import io.soo.springboot.core.enums.AuthProvider
import org.springframework.security.core.authority.SimpleGrantedAuthority

@Component
class LocalLoginSuccessHandler(
    private val userAccountRepository: io.soo.springboot.storage.db.core.UserAccountRepository,
    private val localLoginPolicyService: LocalLoginPolicyService,
    private val loginAttemptService: LoginAttemptService,
    private val userDeviceService: UserDeviceService,
    private val sessionMapService: UserSessionMapService,
    private val deviceBlockService: DeviceBlockService,
    private val objectMapper: ObjectMapper,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val deviceId = request.getHeader(LocalJsonLoginFilter.HEADER_DEVICE_ID)?.takeIf { it.isNotBlank() }

        val principal = authentication.principal as UserPrincipal
        val userId = principal.userId

        // ✅ 로그인 성공 시 실패 카운트/잠금 초기화
        localLoginPolicyService.recordSuccessByUserId(userId, LocalDateTime.now())

        // ✅ 디바이스 차단 체크(차단이면 즉시 로그인 무효화)
        if (deviceBlockService.isBlocked(userId, deviceId)) {
            // 세션 제거
            request.getSession(false)?.invalidate()

            loginAttemptService.record(
                success = false,
                provider = AuthProvider.LOCAL,
                userId = userId,
                providerUserId = principal.username,
                deviceId = deviceId,
                ip = ip,
                userAgent = ua,
                errorCode = "DEVICE_BLOCKED",
                errorMessage = "Blocked device",
            )

            response.sendError(403, "Blocked device")
            return
        }

        // ✅ 세션 확보(스프링 시큐리티가 SecurityContext 저장까지 수행함)
        val session = request.getSession(true)
        val sessionId = session.id
        val deviceIdForMap = deviceId ?: UserDeviceService.UNKNOWN_DEVICE

        // ✅ lastLogin 업데이트
        val user = userAccountRepository.findById(userId).orElseThrow()
        user.lastLoginProvider = AuthProvider.LOCAL
        user.lastLoginAt = Instant.now()
        userAccountRepository.save(user)

        // ✅ 로그인 시도 기록(성공)
        loginAttemptService.record(
            success = true,
            provider = AuthProvider.LOCAL,
            userId = userId,
            providerUserId = principal.username,
            deviceId = deviceIdForMap,
            ip = ip,
            userAgent = ua,
            errorCode = null,
            errorMessage = null,
        )

        // ✅ 디바이스 upsert + 세션 매핑(강제 로그아웃/세션 관리용)
        userDeviceService.upsertLogin(userId, deviceIdForMap, ip, ua)
        sessionMapService.bind(sessionId, userId, deviceIdForMap, AuthProvider.LOCAL)

        // ✅ 응답(JSON) - 컨트롤러 없이도 로그인 결과 반환
        response.status = 200
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val body = mapOf(
            "sessionId" to sessionId,
            "userId" to userId,
            "provider" to "LOCAL",
            "deviceId" to deviceIdForMap,
        )
        objectMapper.writeValue(response.outputStream, body)
    }
}
