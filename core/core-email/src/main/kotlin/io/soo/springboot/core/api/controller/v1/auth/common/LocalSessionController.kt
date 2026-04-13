package io.soo.springboot.core.api.controller.v1.auth.common

import io.soo.springboot.core.api.controller.v1.response.AuthSessionStatusResponse
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.support.response.ApiResponse

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local")
class LocalSessionController {
    @GetMapping("/session", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sessionStatus(
        @AuthenticationPrincipal principal: Any?,
        req: HttpServletRequest,
    ): ApiResponse<AuthSessionStatusResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is AnonymousAuthenticationToken) {
            return ApiResponse.success(req = req, data = AuthSessionStatusResponse(authenticated = false))
        }

        val jwt = principal as? Jwt
        if (jwt != null) {
            val userId = (jwt.claims["uid"] as? Number)?.toLong()
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(authenticated = true, userId = userId, subject = jwt.subject),
            )
        }

        if (principal is UserPrincipal) {
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(
                    authenticated = true,
                    userId = principal.userId,
                    subject = principal.username,
                ),
            )
        }

        if (principal is UserDetails) {
            return ApiResponse.success(
                req = req,
                data = AuthSessionStatusResponse(authenticated = true, subject = principal.username),
            )
        }

        if (principal == null || authentication == null) {
            return ApiResponse.success(req = req, data = AuthSessionStatusResponse(authenticated = false))
        }

        return ApiResponse.success(req = req, data = AuthSessionStatusResponse(authenticated = true))
    }
}
