package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceJpaRepository : JpaRepository<ServiceEntity, Long> {
    fun findByServiceCode(serviceCode: String): ServiceEntity?
    fun findByServiceCodeAndServiceStatus(serviceCode: String, serviceStatus: ServiceStatus): ServiceEntity?
    fun findAllByServiceStatus(serviceStatus: ServiceStatus, pageable: Pageable): Page<ServiceEntity>
}
