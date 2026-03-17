package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceMembershipStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class ServiceMembershipRepositoryImpl(
    private val jpaRepository: ServiceMembershipJpaRepository,
) : ServiceMembershipRepository {

    override fun save(membership: ServiceMembership): ServiceMembership {
        val entity = membership.toEntity()
        val saved = jpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findByServiceIdAndUserId(serviceId: Long, userId: Long): ServiceMembership? {
        return jpaRepository.findByServiceIdAndUserId(serviceId, userId)?.toModel()
    }

    override fun existsActiveMembership(serviceId: Long, userId: Long): Boolean {
        return jpaRepository.findByServiceIdAndUserIdAndMembershipStatus(
            serviceId = serviceId,
            userId = userId,
            membershipStatus = ServiceMembershipStatus.ACTIVE,
        ) != null
    }

    override fun countActiveMemberships(serviceId: Long): Long {
        return jpaRepository.countByServiceIdAndMembershipStatus(serviceId, ServiceMembershipStatus.ACTIVE)
    }

    override fun findByUserId(userId: Long, pageable: Pageable): Page<ServiceMembership> {
        return jpaRepository.findAllByUserId(userId, pageable).map { it.toModel() }
    }

    override fun findByServiceId(serviceId: Long, pageable: Pageable): Page<ServiceMembership> {
        return jpaRepository.findAllByServiceId(serviceId, pageable).map { it.toModel() }
    }

    private fun ServiceMembership.toEntity(): ServiceMembershipEntity {
        return ServiceMembershipEntity(
            serviceId = serviceId,
            userId = userId,
            membershipStatus = membershipStatus,
            membershipRole = membershipRole,
            joinedAt = joinedAt,
            withdrawnAt = withdrawnAt,
            withdrawReason = withdrawReason,
        ).also {
            if (id > 0) it.id = id
        }
    }

    private fun ServiceMembershipEntity.toModel(): ServiceMembership {
        return ServiceMembership(
            id = id,
            serviceId = serviceId,
            userId = userId,
            membershipStatus = membershipStatus,
            membershipRole = membershipRole,
            joinedAt = joinedAt,
            withdrawnAt = withdrawnAt,
            withdrawReason = withdrawReason,
        )
    }
}
