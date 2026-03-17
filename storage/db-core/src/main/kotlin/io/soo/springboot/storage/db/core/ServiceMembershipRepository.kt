package io.soo.springboot.storage.db.core

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ServiceMembershipRepository {
    fun save(membership: ServiceMembership): ServiceMembership
    fun findByServiceIdAndUserId(serviceId: Long, userId: Long): ServiceMembership?
    fun existsActiveMembership(serviceId: Long, userId: Long): Boolean
    fun countActiveMemberships(serviceId: Long): Long
    fun findByUserId(userId: Long, pageable: Pageable): Page<ServiceMembership>
    fun findByServiceId(serviceId: Long, pageable: Pageable): Page<ServiceMembership>
}
