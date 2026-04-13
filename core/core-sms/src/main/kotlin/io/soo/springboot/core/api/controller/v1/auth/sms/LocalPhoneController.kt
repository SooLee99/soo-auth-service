package io.soo.springboot.core.api.controller.v1.auth.sms

import io.soo.springboot.core.api.controller.v1.request.PhoneLoginRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneSignUpRequest
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.phone.account.LocalPhoneAccountService
import io.soo.springboot.core.domain.phone.login.LocalPhoneLoginService
import io.soo.springboot.core.enums.AuthMethod
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
@RequestMapping("/api/v1/auth/local/phone")
class LocalPhoneController(
    private val authMethodConfigService: AuthMethodConfigService,
    private val localPhoneAccountService: LocalPhoneAccountService,
    private val localPhoneLoginService: LocalPhoneLoginService,
) {
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: PhoneSignUpRequest) {
        authMethodConfigService.assertEnabled(AuthMethod.SMS)
        localPhoneAccountService.signUpWithPhone(request.phoneNumber, request.phoneVerificationToken)
    }

    @PostMapping("/login", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun login(
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestBody @Valid request: PhoneLoginRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        authMethodConfigService.assertEnabled(AuthMethod.SMS)
        val tokens = localPhoneLoginService.loginByPhone(
            phoneNumber = request.phoneNumber,
            phoneVerificationToken = request.phoneVerificationToken,
            deviceId = deviceId,
            ipAddress = req.remoteAddr,
            userAgent = req.getHeader("User-Agent"),
        )
        return ApiResponse.success(req = req, data = tokens)
    }
}
