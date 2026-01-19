package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

import io.soo.springboot.storage.db.core.JwtDenylistEntity
import io.soo.springboot.storage.db.core.JwtDenylistRepository

@Service
class JwtDenylistService(
    private val repo: JwtDenylistRepository,
) {

    /**
     * ✅ JWT revoke(블랙리스트 등록)
     * - jti가 없을 수도 있어서 tokenHash 기반도 함께 저장/검증
     */
    @Transactional
    fun revoke(
        tokenValue: String,
        jti: String?,
        expiresAt: Instant?,
        reason: String? = null,
    ) {
        val exp = expiresAt ?: Instant.now().plusSeconds(60 * 60)
        val tokenHash = sha256(tokenValue)

        if (!jti.isNullOrBlank() && repo.existsByJti(jti)) return
        val key = jti?.takeIf { it.isNotBlank() } ?: "HASH:$tokenHash"

        repo.save(
            JwtDenylistEntity(
                jti = key,
                revokedAt = Instant.now(),
                expiresAt = exp,
                reason = reason,
            ),
        )
    }

    /**
     * ✅ (Decoder용) jti 또는 tokenHash로 revoke 여부 확인
     */
    @Transactional(readOnly = true)
    fun isRevoked(jti: String?, tokenValue: String): Boolean {
        if (!jti.isNullOrBlank() && repo.existsByJti(jti)) return true

        val tokenHash = sha256(tokenValue)
        return repo.existsByJti("HASH:$tokenHash")
    }

    /**
     * ✅ 만료된 denylist 레코드 정리
     */
    @Transactional
    fun cleanupExpired(now: Instant = Instant.now()): Int {
        return repo.deleteExpired(now)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
