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
        return ApiResponse.success(req = req, data = healthSnapshotService.publicSummary(toHealthRequest(req)))
    }

    private fun toHealthRequest(req: HttpServletRequest): HealthSnapshotService.HealthRequest =
        HealthSnapshotService.HealthRequest(
            scheme = req.scheme,
            hostHeader = req.getHeader("Host"),
            serverName = req.serverName,
            serverPort = req.serverPort,
            forwardedProto = req.getHeader("X-Forwarded-Proto"),
            forwardedHost = req.getHeader("X-Forwarded-Host"),
            forwardedPort = req.getHeader("X-Forwarded-Port"),
        )
}
