package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.VerificationCodeConfirmRequest
import io.soo.springboot.core.api.controller.v1.request.VerificationCodeRequest
import io.soo.springboot.core.domain.VerificationService
import io.soo.springboot.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/verification")
class VerificationController(
    private val verificationService: VerificationService,
) {
    /**
     * ✅ 인증코드 발송/재발송 (항상 200)
     * POST /api/v1/auth/verification/request
     */
    @PostMapping("/request")
    fun requestCode(@RequestBody req: VerificationCodeRequest): ApiResponse<*> {
        val res = verificationService.requestCode(req.channel, req.identifier)
        return ApiResponse.success(res) // 항상 200
    }

    /**
     * ✅ 인증코드 확인 (항상 200)
     * POST /api/v1/auth/verification/confirm
     */
    @PostMapping("/confirm")
    fun confirmCode(@RequestBody req: VerificationCodeConfirmRequest): ApiResponse<*> {
        val res = verificationService.confirmCode(req.channel, req.identifier, req.code)
        return ApiResponse.success(res) // 항상 200
    }
}
