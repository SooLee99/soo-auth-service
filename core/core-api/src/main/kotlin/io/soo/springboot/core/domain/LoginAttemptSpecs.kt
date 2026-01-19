package io.soo.springboot.core.domain

import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.LoginAttemptEntity

object LoginAttemptSpecs {
    fun filter(
        success: Boolean?,
        provider: AuthProvider?,
        userId: Long?,
        deviceId: String?,
        ip: String?,
        errorCode: String?,
        from: LocalDateTime?,
        to: LocalDateTime?,
    ): Specification<LoginAttemptEntity> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            success?.let { predicates += cb.equal(root.get<Boolean>("success"), it) }
            provider?.let { predicates += cb.equal(root.get<AuthProvider>("provider"), it) }
            userId?.let { predicates += cb.equal(root.get<Long>("userId"), it) }
            deviceId?.takeIf { it.isNotBlank() }?.let { predicates += cb.equal(root.get<String>("deviceId"), it) }
            ip?.takeIf { it.isNotBlank() }?.let { predicates += cb.equal(root.get<String>("ip"), it) }
            errorCode?.takeIf { it.isNotBlank() }?.let { predicates += cb.equal(root.get<String>("errorCode"), it) }

            from?.let { predicates += cb.greaterThanOrEqualTo(root.get("createdAt"), it) }
            to?.let { predicates += cb.lessThanOrEqualTo(root.get("createdAt"), it) }

            cb.and(*predicates.toTypedArray())
        }
    }
}
