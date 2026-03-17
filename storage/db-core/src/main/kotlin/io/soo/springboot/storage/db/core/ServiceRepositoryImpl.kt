package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class ServiceRepositoryImpl(
    private val jpaRepository: ServiceJpaRepository,
) : ServiceRepository {

    override fun save(service: Service): Service {
        val entity = service.toEntity()
        val saved = jpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): Service? {
        return jpaRepository.findById(id).map { it.toModel() }.orElse(null)
    }

    override fun findByServiceCode(serviceCode: String): Service? {
        return jpaRepository.findByServiceCode(serviceCode)?.toModel()
    }

    override fun findActiveByServiceCode(serviceCode: String): Service? {
        return jpaRepository.findByServiceCodeAndServiceStatus(serviceCode, ServiceStatus.ACTIVE)?.toModel()
    }

    override fun findAll(pageable: Pageable): Page<Service> {
        return jpaRepository.findAll(pageable).map { it.toModel() }
    }

    override fun findActive(pageable: Pageable): Page<Service> {
        return jpaRepository.findAllByServiceStatus(ServiceStatus.ACTIVE, pageable).map { it.toModel() }
    }

    private fun Service.toEntity(): ServiceEntity {
        return ServiceEntity(
            serviceCode = serviceCode,
            serviceName = serviceName,
            serviceStatus = serviceStatus,
        ).also {
            if (id > 0) it.id = id
        }
    }

    private fun ServiceEntity.toModel(): Service {
        return Service(
            id = id,
            serviceCode = serviceCode,
            serviceName = serviceName,
            serviceStatus = serviceStatus,
        )
    }
}
