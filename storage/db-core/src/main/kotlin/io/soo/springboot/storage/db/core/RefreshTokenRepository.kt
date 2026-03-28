package io.soo.springboot.storage.db.core

import java.time.Instant

interface RefreshTokenRepository {
    fun save(token: RefreshToken): RefreshToken
    fun findByTokenHash(hash: String): RefreshToken?
    fun findActiveByUserIdAndDeviceId(userId: Long, deviceId: String, serviceId: Long?, now: Instant): List<RefreshToken>
    fun findActiveByUserId(userId: Long, serviceId: Long?, now: Instant): List<RefreshToken>
    fun revokeAllActiveByUserId(userId: Long, serviceId: Long?, now: Instant): Int
    fun revokeAllActiveByUserIdAndDeviceId(userId: Long, deviceId: String, serviceId: Long?, now: Instant): Int
    fun deleteExpired(before: Instant): Int
    fun findForUpdateByHash(hash: String): RefreshToken?
}
