package io.soo.springboot.core.domain

import org.springframework.stereotype.Service

import io.soo.springboot.core.enums.DeviceStatus
import io.soo.springboot.storage.db.core.UserDeviceRepository


@Service
class DeviceBlockService(
    private val userDeviceRepository: UserDeviceRepository,
) {
    fun isBlocked(userId: Long, deviceIdOrNull: String?): Boolean {
        val deviceId = deviceIdOrNull?.takeIf { it.isNotBlank() } ?: UserDeviceService.UNKNOWN_DEVICE
        val device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId) ?: return false
        return device.deviceStatus == DeviceStatus.BLOCKED
    }
}
