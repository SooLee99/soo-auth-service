package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.AdminServiceCreateRequest
import io.soo.springboot.core.api.controller.v1.response.AdminServiceResponse
import io.soo.springboot.core.domain.AdminServiceManagementService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/admin/services")
class AdminServiceController(
    private val adminServiceManagementService: AdminServiceManagementService,
) {

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun registerService(
        @RequestBody @Valid body: AdminServiceCreateRequest,
        req: HttpServletRequest,
    ): ApiResponse<AdminServiceResponse> {
        val created = adminServiceManagementService.registerService(
            serviceCode = body.serviceCode,
            serviceName = body.serviceName,
        )
        return ApiResponse.success(req = req, data = AdminServiceResponse.from(created))
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listServices(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        req: HttpServletRequest,
    ): ApiResponse<Page<AdminServiceResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"))
        val result = adminServiceManagementService.listServices(pageable).map { AdminServiceResponse.from(it) }
        return ApiResponse.success(req = req, data = result)
    }

    @PostMapping("/{serviceCode}/deactivate", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deactivateService(
        @PathVariable serviceCode: String,
        req: HttpServletRequest,
    ): ApiResponse<AdminServiceResponse> {
        val deactivated = adminServiceManagementService.deactivateService(serviceCode)
        return ApiResponse.success(req = req, data = AdminServiceResponse.from(deactivated))
    }
}

