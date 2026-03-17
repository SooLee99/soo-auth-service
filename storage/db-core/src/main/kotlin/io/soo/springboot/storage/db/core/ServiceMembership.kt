package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceMembershipRole
import io.soo.springboot.core.enums.ServiceMembershipStatus
import java.time.Instant

data class ServiceMembership(
    val id: Long = 0L,
    val serviceId: Long,
    val userId: Long,
    val membershipStatus: ServiceMembershipStatus = ServiceMembershipStatus.ACTIVE,
    val membershipRole: ServiceMembershipRole = ServiceMembershipRole.USER,
    val joinedAt: Instant = Instant.now(),
    val withdrawnAt: Instant? = null,
    val withdrawReason: String? = null,
)

