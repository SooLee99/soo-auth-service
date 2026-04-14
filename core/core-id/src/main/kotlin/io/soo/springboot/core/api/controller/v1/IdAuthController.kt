package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.IdLoginRequest
import io.soo.springboot.core.api.controller.v1.request.IdSignUpRequest
import io.soo.springboot.core.domain.auth.AuthFeature
import io.soo.springboot.core.domain.auth.AuthFeatureGuard
import io.soo.springboot.core.domain.id.IdAccountService
import io.soo.springboot.core.domain.id.IdLoginCommand
import io.soo.springboot.core.domain.id.IdLoginService
import io.soo.springboot.core.domain.id.IdSignUpCommand
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local/id")
class IdAuthController(
    private val authFeatureGuard: AuthFeatureGuard,
    private val idAccountService: IdAccountService,
    private val idLoginService: IdLoginService,
) {
    @PostMapping("/signup")
    fun signUpWithId(@RequestBody @Valid request: IdSignUpRequest) {
        authFeatureGuard.assertEnabled(AuthFeature.ID)
        idAccountService.signUp(
            IdSignUpCommand(
                loginId = request.loginId,
                password = request.password,
                phoneNumber = request.phoneNumber,
                phoneVerificationToken = request.phoneVerificationToken,
            ),
        )
    }

    @PostMapping("/login", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun loginWithId(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody @Valid request: IdLoginRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        authFeatureGuard.assertEnabled(AuthFeature.ID)
        val tokens = idLoginService.login(
            IdLoginCommand(
                loginId = request.loginId,
                password = request.password,
                deviceId = deviceId,
                ipAddress = req.remoteAddr,
                userAgent = req.getHeader("User-Agent"),
            ),
        )
        return ApiResponse.success(req = req, data = tokens)
    }
}
