package io.soo.springboot.core.api.controller.v1.admin.authmethod

import io.soo.springboot.core.api.controller.v1.request.AdminAuthMethodUpdateRequest
import io.soo.springboot.core.api.controller.v1.response.AdminAuthMethodResponse
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/admin/auth-methods")
class AdminAuthMethodController(
    private val authMethodConfigService: AuthMethodConfigService,
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(req: HttpServletRequest): ApiResponse<List<AdminAuthMethodResponse>> {
        val data = authMethodConfigService.list().map(AdminAuthMethodResponse::from)
        return ApiResponse.success(req = req, data = data)
    }

    @PatchMapping("/{method}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @PathVariable method: String,
        @RequestBody @Valid body: AdminAuthMethodUpdateRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminAuthMethodResponse> {
        val authMethod = parseMethod(method)
        val enabled = body.enabled ?: throw CoreException(ErrorType.INVALID_REQUEST_BODY)
        val saved = authMethodConfigService.setEnabled(authMethod, enabled)
        return ApiResponse.success(req = req, data = AdminAuthMethodResponse.from(saved))
    }

    private fun parseMethod(method: String): AuthMethod {
        return try {
            AuthMethod.valueOf(method.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw CoreException(
                ErrorType.INVALID_PARAMETER,
                data = mapOf("method" to method),
            )
        }
    }
}
