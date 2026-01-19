package io.soo.springboot.core.domain

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import io.soo.springboot.core.enums.DeviceStatus
import io.soo.springboot.storage.db.core.UserDeviceEntity
import io.soo.springboot.storage.db.core.UserDeviceRepository

@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
) {
    companion object {
        const val UNKNOWN_DEVICE = "unknown"
        private const val MAX_DEVICE_ID = 255
        private const val MAX_IP = 100
        private const val MAX_UA = 1000
    }

    private fun normalizeDeviceId(deviceIdOrNull: String?): String =
        deviceIdOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_DEVICE_ID)
            ?: UNKNOWN_DEVICE

    @Transactional
    fun upsertLogin(userId: Long, deviceIdOrNull: String?, ip: String?, ua: String?) {
        val deviceId = normalizeDeviceId(deviceIdOrNull)
        val now = LocalDateTime.now()

        // 1차 시도: 락 조회 → 없으면 생성 → 저장/flush
        try {
            val device = userDeviceRepository.lockByUserIdAndDeviceId(userId, deviceId)
                ?: UserDeviceEntity(
                    userId = userId,
                    deviceId = deviceId,
                    deviceStatus = DeviceStatus.ACTIVE,
                )

            device.lastLoginAt = now

            // null/blank는 기존값 유지 (정보가 "사라지는" 문제 방지)
            ip?.trim()?.takeIf { it.isNotBlank() }?.let { device.lastIp = it.take(MAX_IP) }
            ua?.trim()?.takeIf { it.isNotBlank() }?.let { device.lastUserAgent = it.take(MAX_UA) }

            // @UpdateTimestamp가 updated_at 갱신
            userDeviceRepository.saveAndFlush(device)
            return
        } catch (_: DataIntegrityViolationException) {
            // 동시에 insert 경쟁으로 uq(user_id, device_id) 충돌 가능 → 아래에서 재조회 후 업데이트
        }

        // 2차 시도(재시도): 이미 누군가 insert한 row를 다시 잡아서 업데이트
        val existing = userDeviceRepository.lockByUserIdAndDeviceId(userId, deviceId)
            ?: throw IllegalStateException("Upsert retry failed: userId=$userId deviceId=$deviceId")

        existing.lastLoginAt = now
        ip?.trim()?.takeIf { it.isNotBlank() }?.let { existing.lastIp = it.take(MAX_IP) }
        ua?.trim()?.takeIf { it.isNotBlank() }?.let { existing.lastUserAgent = it.take(MAX_UA) }

        userDeviceRepository.save(existing)
    }

    fun list(userId: Long): List<UserDeviceEntity> =
        userDeviceRepository.findAllByUserId(userId)

    @Transactional
    fun block(userId: Long, deviceId: String, reason: String?) {
        val now = LocalDateTime.now()
        val normalizedDeviceId = normalizeDeviceId(deviceId)

        val device = userDeviceRepository.lockByUserIdAndDeviceId(userId, normalizedDeviceId)
            ?: UserDeviceEntity(userId = userId, deviceId = normalizedDeviceId)

        device.deviceStatus = DeviceStatus.BLOCKED
        device.blockedReason = reason
        device.blockedAt = now

        userDeviceRepository.save(device)
    }

    @Transactional
    fun unblock(userId: Long, deviceId: String) {
        val normalizedDeviceId = normalizeDeviceId(deviceId)
        val device = userDeviceRepository.lockByUserIdAndDeviceId(userId, normalizedDeviceId) ?: return

        device.deviceStatus = DeviceStatus.ACTIVE
        device.blockedReason = null
        device.blockedAt = null

        userDeviceRepository.save(device)
    }
}
