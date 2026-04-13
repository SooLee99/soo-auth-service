package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.domain.health.HealthSnapshotService
import io.soo.springboot.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminHealthController(
    private val healthSnapshotService: HealthSnapshotService,
) {
    @GetMapping("/api/v1/auth/admin/health")
    fun getAdminHealth(req: HttpServletRequest): ApiResponse<Map<String, Any>> {
        val request = HealthSnapshotService.HealthRequest(
            scheme = req.scheme,
            hostHeader = req.getHeader("Host"),
            serverName = req.serverName,
            serverPort = req.serverPort,
            forwardedProto = req.getHeader("X-Forwarded-Proto"),
            forwardedHost = req.getHeader("X-Forwarded-Host"),
            forwardedPort = req.getHeader("X-Forwarded-Port"),
        )
        return ApiResponse.success(req = req, data = healthSnapshotService.adminDetails(request))
    }
}
