package io.soo.springboot.core.api.controller.v1.auth.sms

import io.soo.springboot.core.api.controller.v1.request.PhoneVerificationConfirmRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneVerificationIssueRequest
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationService
import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/local/phone-verifications")
@ConditionalOnProperty(name = ["app.auth.method.sms.enabled"], havingValue = "true", matchIfMissing = true)
class LocalPhoneVerificationController(
    private val authMethodConfigService: AuthMethodConfigService,
    private val phoneVerificationService: PhoneVerificationService,
) {
    @PostMapping("/request", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun issueVerification(
        @RequestBody @Valid request: PhoneVerificationIssueRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        authMethodConfigService.assertEnabled(AuthMethod.SMS)
        val issued = phoneVerificationService.createVerification(request.phoneNumber)
        return ApiResponse.success(
            req = req,
            data = mapOf(
                "verificationId" to issued.verificationId,
                "expiresInSec" to issued.expiresInSec,
            ),
        )
    }

    @PostMapping("/confirm", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun confirmVerification(
        @RequestBody @Valid request: PhoneVerificationConfirmRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        authMethodConfigService.assertEnabled(AuthMethod.SMS)
        val confirmed = phoneVerificationService.confirmVerification(
            phoneNumber = request.phoneNumber,
            verificationId = request.verificationId,
            code = request.code,
        )
        return ApiResponse.success(
            req = req,
            data = mapOf(
                "phoneVerificationToken" to confirmed.proofToken,
                "expiresInSec" to confirmed.expiresInSec,
            ),
        )
    }
}
