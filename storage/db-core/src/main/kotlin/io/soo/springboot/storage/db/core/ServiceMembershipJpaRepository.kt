package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceMembershipStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceMembershipJpaRepository : JpaRepository<ServiceMembershipEntity, Long> {
    fun findByServiceIdAndUserId(serviceId: Long, userId: Long): ServiceMembershipEntity?
    fun findByServiceIdAndUserIdAndMembershipStatus(
        serviceId: Long,
        userId: Long,
        membershipStatus: ServiceMembershipStatus,
    ): ServiceMembershipEntity?

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<ServiceMembershipEntity>
    fun findAllByServiceId(serviceId: Long, pageable: Pageable): Page<ServiceMembershipEntity>
    fun countByServiceIdAndMembershipStatus(serviceId: Long, membershipStatus: ServiceMembershipStatus): Long
}
