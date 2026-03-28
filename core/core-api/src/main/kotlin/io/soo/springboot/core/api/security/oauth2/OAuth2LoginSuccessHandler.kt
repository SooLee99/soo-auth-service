package io.soo.springboot.core.api.security.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.controller.v1.response.LoginSuccessResponse
import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.api.security.userdetails.UserPrincipalLoader
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import io.soo.springboot.core.domain.admin.ServiceContextResolver
import io.soo.springboot.core.domain.admin.ServiceMembershipAccessService
import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.domain.oauth2.OAuth2Login
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.support.error.CoreException
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
    private val oAuth2LoginUseCase: OAuth2Login,
    private val userPrincipalLoader: UserPrincipalLoader,
    private val errorWriter: SecurityErrorResponseWriter,
    private val serviceContextResolver: ServiceContextResolver,
    private val serviceMembershipAccessService: ServiceMembershipAccessService,

    private val jwtService: AuthTokenManager,
    private val loginHistoryService: LoginHistoryService,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val session = request.getSession(false)
        val headerDeviceId = request.getHeader("X-Device-Id")?.trim()
        val sessionDeviceId = session?.getAttribute("DEVICE_ID") as? String
        val deviceId = headerDeviceId?.takeIf { it.isNotBlank() } ?: sessionDeviceId.orEmpty()
        val scopedServiceCode = (session?.getAttribute("SERVICE_CODE") as? String)?.takeIf { it.isNotBlank() }
        val userId = try {
            oAuth2LoginUseCase.userId(authentication)
        } catch (_: AccountStatusDeniedException) {
            errorWriter.writeError(response, request, ErrorType.LOGIN_DENIED)
            return
        } catch (e: CoreException) {
            errorWriter.writeError(response, request, e.errorType, fields = e.data)
            return
        }
        val principal = try {
            userPrincipalLoader.loadByUserId(userId)
        } catch (e: CoreException) {
            errorWriter.writeError(response, request, e.errorType, fields = e.data)
            return
        }
        val provider: AuthProvider = principal.provider
        val appAuth = UsernamePasswordAuthenticationToken(principal.username, null, principal.authorities)
        val scopedService = if (scopedServiceCode != null) serviceContextResolver.resolveActive(scopedServiceCode) else null
        if (scopedService != null) {
            serviceMembershipAccessService.ensureActiveMembershipOrCreate(scopedService.id, userId)
        }

        val issued = jwtService.issue(
            authentication = appAuth,
            userId = userId,
            deviceId = deviceId,
            provider = provider,
            serviceId = scopedService?.id,
        )

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
                email = principal.email,
                roles = roles,
            )
        )

        // 로그인 성공 기록
        loginHistoryService.recordLoginSuccess(
            userId = userId,
            userEmail = principal.email,
            loginType = LoginType.OAUTH2,
            ipAddress = ip,
            userAgent = ua,
            deviceId = deviceId,
        )

        if (session != null) {
            session.removeAttribute("SERVICE_CODE")
            session.removeAttribute("SERVICE_ID")
            session.removeAttribute("RETURN_URL")
            session.removeAttribute("DEVICE_ID")
        }

        response.status = HttpServletResponse.SC_OK
        response.characterEncoding = "UTF-8"
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.use {
            it.write(objectMapper.writeValueAsString(
                ApiResponse.success(req = request, data = payload))
            )
        }
    }
}
