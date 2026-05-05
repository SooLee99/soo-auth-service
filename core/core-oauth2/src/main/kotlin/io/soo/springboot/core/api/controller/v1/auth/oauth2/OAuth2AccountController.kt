package io.soo.springboot.core.api.controller.v1.auth.oauth2

import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@ConditionalOnProperty(name = ["app.auth.method.oauth2.enabled"], havingValue = "true", matchIfMissing = true)
class OAuth2AccountController(
    private val authMethodConfigService: AuthMethodConfigService,
) {

    @GetMapping("/{provider}/authorize-url")
    fun getAuthorizeUrl(
        @PathVariable provider: String,
        @RequestParam(required = false) returnUrl: String?,
        @RequestHeader(name = "X-Device-Id", required = false) deviceId: String?,
        session: HttpSession,
        req: HttpServletRequest,
    ): ApiResponse<out String> {
        authMethodConfigService.assertEnabled(AuthMethod.OAUTH2)
        if (returnUrl != null && !isRelativeReturnUrl(returnUrl)) {
            return ApiResponse.error(
                type = ErrorType.INVALID_REQUEST,
                message = "returnUrl은 상대경로만 허용합니다.",
                req = req,
            )
        }

        session.setAttribute("RETURN_URL", returnUrl)
        session.setAttribute("DEVICE_ID", deviceId)

        val authorizePath = "/oauth2/authorization/$provider"
        return ApiResponse.success(req = req, data = authorizePath)
    }

    private fun isRelativeReturnUrl(returnUrl: String): Boolean {
        return returnUrl.startsWith("/") &&
            !returnUrl.startsWith("//") &&
            !returnUrl.contains("://")
    }
}
