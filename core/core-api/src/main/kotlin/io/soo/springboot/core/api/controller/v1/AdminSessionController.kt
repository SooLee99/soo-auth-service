package io.soo.springboot.core.api.controller.v1

import io.soo.springboot.core.api.controller.v1.request.RevokeAllSessionsRequest
import io.soo.springboot.core.api.controller.v1.request.RevokeSessionRequest
import io.soo.springboot.core.api.controller.v1.response.AdminSessionResponse
import io.soo.springboot.core.domain.AdminSessionCommandService
import io.soo.springboot.core.domain.AdminSessionQueryService
import io.soo.springboot.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
class AdminSessionController(
    private val sessionCommand: AdminSessionCommandService,
    private val sessionQuery: AdminSessionQueryService,
) {

    /**
     * ✅ 유저 세션 목록 조회
     * GET /api/v1/admin/users/{userId}/sessions?activeOnly=true&deviceId=...
     *
     * - activeOnly=true면 revokedAt=null만 조회
     * - deviceId가 있으면 해당 디바이스만 조회
     */
    @GetMapping("/users/{userId}/sessions")
    fun listUserSessions(
        @PathVariable userId: Long,
        @RequestParam(required = false) deviceId: String?,
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
    ): ApiResponse<List<AdminSessionResponse>> {
        val items = sessionQuery.listUserSessions(
            userId = userId,
            deviceId = deviceId,
            activeOnly = activeOnly,
        )
        return ApiResponse.success(items)
    }

    /**
     * ✅ 세션 단건 상세 조회
     * GET /api/v1/admin/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    fun getSessionDetail(
        @PathVariable sessionId: String,
    ): ApiResponse<AdminSessionResponse> {
        val dto = sessionQuery.getSessionDetail(sessionId)
        return ApiResponse.success(dto)
    }

    /**
     * ✅ 특정 세션 강제 종료(DB revoke)
     * POST /api/v1/admin/sessions/{sessionId}/revoke
     */
    @PostMapping("/sessions/{sessionId}/revoke")
    fun revokeSession(
        @PathVariable sessionId: String,
        @RequestBody req: RevokeSessionRequest,
    ): ApiResponse<Unit> {
        sessionCommand.revokeSession(sessionId, req.reason)
        return ApiResponse.success(Unit)
    }

    /**
     * ✅ 유저 세션 전체(또는 특정 device) 강제 종료
     * POST /api/v1/admin/users/{userId}/sessions/revoke
     */
    @PostMapping("/users/{userId}/sessions/revoke")
    fun revokeAllSessions(
        @PathVariable userId: Long,
        @RequestBody req: RevokeAllSessionsRequest,
    ): ApiResponse<Unit> {
        sessionCommand.revokeAllSessions(userId, req.deviceId, req.reason)
        return ApiResponse.success(Unit)
    }
}
