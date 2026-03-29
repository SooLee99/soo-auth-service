package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.domain.health.HealthSnapshotService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val healthSnapshotService: HealthSnapshotService,
) {
    @GetMapping("/health")
    fun getHealth(req: HttpServletRequest): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(req = req, data = healthSnapshotService.publicSummary(req))
    }

    @GetMapping("/api/v1/auth/admin/health")
    fun getAdminHealth(req: HttpServletRequest): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(req = req, data = healthSnapshotService.adminDetails(req))
    }
}
