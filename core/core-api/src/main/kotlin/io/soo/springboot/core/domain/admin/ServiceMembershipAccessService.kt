package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.ServiceMembershipStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.ServiceMembership
import io.soo.springboot.storage.db.core.ServiceMembershipRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ServiceMembershipAccessService(
    private val serviceMembershipRepository: ServiceMembershipRepository,
) {
    @Transactional
    fun ensureActiveMembershipOrCreate(serviceId: Long, userId: Long): ServiceMembership {
        val existing = serviceMembershipRepository.findByServiceIdAndUserId(serviceId, userId)
        if (existing == null) {
            return serviceMembershipRepository.save(ServiceMembership(serviceId = serviceId, userId = userId))
        }

        return when (existing.membershipStatus) {
            ServiceMembershipStatus.ACTIVE -> existing
            ServiceMembershipStatus.WITHDRAWN -> {
                serviceMembershipRepository.save(
                    existing.copy(
                        membershipStatus = ServiceMembershipStatus.ACTIVE,
                        joinedAt = Instant.now(),
                        withdrawnAt = null,
                        withdrawReason = null,
                    )
                )
            }
            ServiceMembershipStatus.BLOCKED -> {
                throw CoreException(
                    ErrorType.FORBIDDEN,
                    data = mapOf("reason" to "SERVICE_MEMBERSHIP_BLOCKED", "serviceId" to serviceId, "userId" to userId),
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun ensureActiveMembership(serviceId: Long, userId: Long): ServiceMembership {
        val membership = serviceMembershipRepository.findByServiceIdAndUserId(serviceId, userId)
            ?: throw CoreException(
                ErrorType.FORBIDDEN,
                data = mapOf("reason" to "SERVICE_MEMBERSHIP_REQUIRED", "serviceId" to serviceId, "userId" to userId),
            )

        if (membership.membershipStatus != ServiceMembershipStatus.ACTIVE) {
            throw CoreException(
                ErrorType.FORBIDDEN,
                data = mapOf(
                    "reason" to "SERVICE_MEMBERSHIP_NOT_ACTIVE",
                    "serviceId" to serviceId,
                    "userId" to userId,
                    "membershipStatus" to membership.membershipStatus.name,
                ),
            )
        }
        return membership
    }

    @Transactional
    fun withdrawMembership(serviceId: Long, userId: Long, reason: String?): ServiceMembership {
        val membership = ensureActiveMembership(serviceId, userId)
        val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }
        return serviceMembershipRepository.save(
            membership.copy(
                membershipStatus = ServiceMembershipStatus.WITHDRAWN,
                withdrawnAt = Instant.now(),
                withdrawReason = trimmedReason,
            )
        )
    }

    fun assertTokenServiceScope(jwt: Jwt, serviceId: Long) {
        val tokenServiceId = (jwt.claims["sid"] as? Number)?.toLong()
        if (tokenServiceId == null || tokenServiceId != serviceId) {
            throw CoreException(
                ErrorType.FORBIDDEN,
                data = mapOf(
                    "reason" to "SERVICE_SCOPE_MISMATCH",
                    "serviceId" to serviceId,
                    "tokenServiceId" to tokenServiceId,
                ),
            )
        }
    }
}
