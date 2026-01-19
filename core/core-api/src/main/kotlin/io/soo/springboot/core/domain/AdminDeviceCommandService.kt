package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import io.soo.springboot.core.enums.DeviceStatus
import io.soo.springboot.storage.db.core.UserDeviceRepository

@Service
class AdminDeviceCommandService(
    private val userDeviceRepository: UserDeviceRepository,
) {

    @Transactional
    fun blockDevice(userId: Long, deviceId: String, reason: String) {
        val d = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw NoSuchElementException("Device not found. userId=$userId deviceId=$deviceId")

        d.deviceStatus = DeviceStatus.BLOCKED
        d.blockedReason = reason
        d.blockedAt = LocalDateTime.now()
        userDeviceRepository.save(d)
    }

    @Transactional
    fun unblockDevice(userId: Long, deviceId: String) {
        val d = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw NoSuchElementException("Device not found. userId=$userId deviceId=$deviceId")

        d.deviceStatus = DeviceStatus.ACTIVE
        d.blockedReason = null
        d.blockedAt = null
        userDeviceRepository.save(d)
    }
}
