package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import io.soo.springboot.core.api.controller.v1.response.AdminUserDeviceDto
import io.soo.springboot.storage.db.core.UserDeviceRepository

@Service
class AdminDeviceQueryService(
    private val userDeviceRepository: UserDeviceRepository,
) {

    /**
     * ✅ 특정 유저의 디바이스 목록 조회
     * - 운영 콘솔에서 “디바이스 목록 먼저 보고 조치”가 일반적이므로 별도 API 권장
     */
    @Transactional(readOnly = true)
    fun listUserDevices(userId: Long): List<AdminUserDeviceDto> {
        return userDeviceRepository.findAllByUserId(userId).map { e ->
            // TODO: DTO에서 메서드로 묶기
            AdminUserDeviceDto(
                id = e.id,
                userId = e.userId,
                deviceId = e.deviceId,
                status = e.deviceStatus,
                blockedReason = e.blockedReason,
                blockedAt = e.blockedAt,
                lastLoginAt = e.lastLoginAt,
                lastIp = e.lastIp,
                lastUserAgent = e.lastUserAgent,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
        }
    }
}
