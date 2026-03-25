package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.PhoneVerificationConfirmRequest
import io.soo.springboot.core.api.controller.v1.request.PhoneVerificationIssueRequest
import io.soo.springboot.core.domain.admin.ServiceContextResolver
import io.soo.springboot.core.domain.local.phone.PhoneVerificationService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services/{serviceCode}/auth/local/phone-verifications")
class ServiceLocalPhoneVerificationController(
    private val serviceContextResolver: ServiceContextResolver,
    private val phoneVerificationService: PhoneVerificationService,
) {
    @PostMapping("/request", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun request(
        @PathVariable serviceCode: String,
        @RequestBody @Valid request: PhoneVerificationIssueRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        val issued = phoneVerificationService.issue(request.phoneNumber)
        return ApiResponse.success(
            req = req,
            data = mapOf(
                "serviceCode" to resolvedService.serviceCode,
                "verificationId" to issued.verificationId,
                "expiresInSec" to issued.expiresInSec,
            ),
        )
    }

    @PostMapping("/confirm", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun confirm(
        @PathVariable serviceCode: String,
        @RequestBody @Valid request: PhoneVerificationConfirmRequest,
        req: HttpServletRequest,
    ): ApiResponse<Any?> {
        val resolvedService = serviceContextResolver.resolveActive(serviceCode)
        val confirmed = phoneVerificationService.confirm(
            phoneNumber = request.phoneNumber,
            verificationId = request.verificationId,
            code = request.code,
        )
        return ApiResponse.success(
            req = req,
            data = mapOf(
                "serviceCode" to resolvedService.serviceCode,
                "phoneVerificationToken" to confirmed.proofToken,
                "expiresInSec" to confirmed.expiresInSec,
            ),
        )
    }
}
