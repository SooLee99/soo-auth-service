package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.PhoneLoginRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.domain.auth.AuthFeature
import io.soo.springboot.core.domain.auth.AuthFeatureGuard
import io.soo.springboot.core.domain.phone.account.LocalPhoneAccountService
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginCommand
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginService
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
@RequestMapping("/api/v1/auth/local")
class PhoneAuthController(
    private val authFeatureGuard: AuthFeatureGuard,
    private val localPhoneAccountService: LocalPhoneAccountService,
    private val localPhoneLoginService: LocalPhoneLoginService,
) {
    @PostMapping(value = ["/phone/signup", "/signup/phone"])
    fun signUpWithPhone(@RequestBody @Valid request: PhoneSignUpRequest) {
        authFeatureGuard.assertEnabled(AuthFeature.PHONE)
        localPhoneAccountService.signUpWithPhone(request.phoneNumber, request.phoneVerificationToken)
    }

    @PostMapping(value = ["/phone/login", "/login/phone"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun loginWithPhone(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody @Valid request: PhoneLoginRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        authFeatureGuard.assertEnabled(AuthFeature.PHONE)
        val tokens = localPhoneLoginService.login(
            LocalPhoneLoginCommand(
                phoneNumber = request.phoneNumber,
                phoneVerificationToken = request.phoneVerificationToken,
                deviceId = deviceId,
                ipAddress = req.remoteAddr,
                userAgent = req.getHeader("User-Agent"),
            ),
        )
        return ApiResponse.success(req = req, data = tokens)
    }
}
