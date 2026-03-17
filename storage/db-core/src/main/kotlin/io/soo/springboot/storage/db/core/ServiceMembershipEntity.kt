package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceMembershipRole
import io.soo.springboot.core.enums.ServiceMembershipStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "service_membership",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_membership_service_user", columnNames = ["serviceId", "userId"]),
    ],
    indexes = [
        Index(name = "ix_membership_user_status", columnList = "userId, membershipStatus"),
        Index(name = "ix_membership_service_status", columnList = "serviceId, membershipStatus"),
    ],
)
class ServiceMembershipEntity(
    @Column(nullable = false)
    var serviceId: Long,

    @Column(nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var membershipStatus: ServiceMembershipStatus = ServiceMembershipStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var membershipRole: ServiceMembershipRole = ServiceMembershipRole.USER,

    @Column(nullable = false)
    var joinedAt: Instant = Instant.now(),

    @Column
    var withdrawnAt: Instant? = null,

    @Column(length = 500)
    var withdrawReason: String? = null,
) : BaseEntity()

