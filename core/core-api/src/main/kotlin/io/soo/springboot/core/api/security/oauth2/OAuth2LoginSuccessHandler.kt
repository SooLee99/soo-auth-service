package io.soo.springboot.core.api.security.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.controller.v1.response.LoginSuccessResponse
import io.soo.springboot.core.api.security.auth.UserIdResolver
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.userdetails.UserPrincipalLoader
import io.soo.springboot.core.enums.AuthProvider
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
    private val jwtService: AuthTokenManager,
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
        val userId = userIdResolver.resolve(authentication)
        val principal = userPrincipalLoader.loadByUserId(userId)
        val provider: AuthProvider = principal.provider
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