package io.soo.springboot.core.api.security.handler

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.controller.v1.response.LoginSuccessResponse
import io.soo.springboot.core.domain.token.UserPrincipal
import io.soo.springboot.core.domain.token.JwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class LocalLoginSuccessHandler(
    private val objectMapper: ObjectMapper,
    private val jwtService: JwtService,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as UserPrincipal
        val roles = authentication.authorities.map { it.authority }.sorted()
        val issued = jwtService.issue(authentication, principal.userId)

        val dto = LoginSuccessResponse(
            data = LoginSuccessResponse.Data(
                token = LoginSuccessResponse.Token(
                    accessToken = issued.accessToken,
                    expiresIn = issued.accessExpiresInSec,
                    refreshToken = issued.refreshToken,
                    refreshExpiresIn = issued.refreshExpiresInSec,
                ),
                user = LoginSuccessResponse.User(
                    id = principal.userId,
                    provider = "LOCAL",
                    email = principal.email,
                    roles = roles,
                )
            )
        )

        response.status = 200
        response.contentType = "${MediaType.APPLICATION_JSON_VALUE};charset=UTF-8"
        objectMapper.writeValue(response.outputStream, dto)
    }
}
