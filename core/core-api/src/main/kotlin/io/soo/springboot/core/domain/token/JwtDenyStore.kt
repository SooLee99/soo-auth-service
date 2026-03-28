package io.soo.springboot.core.domain.token

import java.time.Duration
import java.time.Instant

interface JwtDenyStore {
    /**
     * jti를 denylist에 추가 (ttl 동안)
     */
    fun deny(jti: String, ttl: Duration)

    /**
     * jti가 denylist에 있고(존재) 아직 만료되지 않았는지
     */
    fun isDenied(jti: String): Boolean

    /**
     * (옵션) 만료된 레코드 정리
     * @return 삭제된 행 수
     */
    fun purge(before: Instant = Instant.now()): Long
}
