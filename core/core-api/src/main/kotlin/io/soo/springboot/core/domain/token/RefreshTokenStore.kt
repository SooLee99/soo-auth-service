package io.soo.springboot.core.domain.token

import java.time.Instant

data class RefreshTokenRecord(
    val token: String,
    val userId: Long,
    val expiresAt: Instant,
)

interface RefreshTokenStore {
    fun save(record: RefreshTokenRecord)

    /**
     * oldToken을 1회용으로 소모(used 처리)하고, newRecord를 저장
     * - 성공 시 oldToken의 userId를 찾아 Success(userId) 반환
     */
    fun rotate(oldToken: String, newRecord: RefreshTokenRecord): RotateResult

    /** 단일 refresh token 폐기 */
    fun revoke(token: String)

    /** 사용자 전체 refresh token 폐기 */
    fun revokeAllByUser(userId: Long)

    /** (옵션) 만료된 토큰 정리 */
    fun purgeExpired(before: Instant = Instant.now()): Long
}
