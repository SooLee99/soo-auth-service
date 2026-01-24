package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.BlockDeviceRequest
import io.soo.springboot.core.api.controller.v1.request.RevokeDeviceSessionsRequest
import io.soo.springboot.core.api.controller.v1.response.AdminUserDevicResponse
import io.soo.springboot.core.domain.AdminDeviceCommandService
import io.soo.springboot.core.domain.AdminDeviceQueryService
import io.soo.springboot.core.domain.AdminSessionCommandService
import io.soo.springboot.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/devices")
class AdminDeviceController(
    private val deviceCommand: AdminDeviceCommandService,
    private val deviceQuery: AdminDeviceQueryService,
    private val sessionCommand: AdminSessionCommandService,
) {

    /**
     * ✅ 디바이스 목록 조회
     * GET /api/v1/admin/users/{userId}/devices
     */
    @GetMapping
    fun listDevices(
        @PathVariable userId: Long,
    ): ApiResponse<List<AdminUserDevicResponse>> {
        val items = deviceQuery.listUserDevices(userId)
        return ApiResponse.success(items)
    }

    /**
     * ✅ 디바이스 차단
     * POST /api/v1/admin/users/{userId}/devices/{deviceId}/block
     */
    @PostMapping("/{deviceId}/block")
    fun blockDevice(
        @PathVariable userId: Long,
        @PathVariable deviceId: String,
        @RequestBody req: BlockDeviceRequest,
    ): ApiResponse<Unit> {
        deviceCommand.blockDevice(userId, deviceId, req.reason)
        return ApiResponse.success(Unit)
    }

    /**
     * ✅ 디바이스 차단 해제
     * POST /api/v1/admin/users/{userId}/devices/{deviceId}/unblock
     */
    @PostMapping("/{deviceId}/unblock")
    fun unblockDevice(
        @PathVariable userId: Long,
        @PathVariable deviceId: String,
    ): ApiResponse<Unit> {
        deviceCommand.unblockDevice(userId, deviceId)
        return ApiResponse.success(Unit)
    }

    /**
     * ✅ 해당 디바이스로 로그인된 "활성 세션" 강제 종료
     * POST /api/v1/admin/users/{userId}/devices/{deviceId}/sessions/revoke
     */
    @PostMapping("/{deviceId}/sessions/revoke")
    fun revokeDeviceSessions(
        @PathVariable userId: Long,
        @PathVariable deviceId: String,
        @RequestBody req: RevokeDeviceSessionsRequest,
    ): ApiResponse<Unit> {
        sessionCommand.revokeAllSessions(
            userId = userId,
            deviceId = deviceId,
            reason = req.reason,
        )
        return ApiResponse.success(Unit)
    }
}
