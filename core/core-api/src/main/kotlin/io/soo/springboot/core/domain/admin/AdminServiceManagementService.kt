package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.ServiceStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.ServiceMembershipRepository
import io.soo.springboot.storage.db.core.ServiceRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminServiceManagementService(
    private val serviceRepository: ServiceRepository,
    private val serviceMembershipRepository: ServiceMembershipRepository,
    private val serviceContextResolver: ServiceContextResolver,
) {
    companion object {
        private const val DEFAULT_SERVICE_CODE = "DEFAULT"
    }

    @Transactional
    fun registerService(serviceCode: String, serviceName: String): io.soo.springboot.storage.db.core.Service {
        val normalizedCode = serviceContextResolver.normalizeAndValidate(serviceCode)
        val normalizedName = serviceName.trim()
        if (normalizedName.isBlank()) {
            throw CoreException(ErrorType.INVALID_PARAMETER, data = mapOf("serviceName" to serviceName))
        }

        if (serviceRepository.findByServiceCode(normalizedCode) != null) {
            throw CoreException(ErrorType.DUPLICATE_SERVICE_CODE, data = mapOf("serviceCode" to normalizedCode))
        }

        return serviceRepository.save(
            io.soo.springboot.storage.db.core.Service(
                serviceCode = normalizedCode,
                serviceName = normalizedName,
                serviceStatus = ServiceStatus.ACTIVE,
            )
        )
    }

    @Transactional(readOnly = true)
    fun listServices(pageable: Pageable): Page<io.soo.springboot.storage.db.core.Service> {
        return serviceRepository.findAll(pageable)
    }

    @Transactional
    fun deactivateService(serviceCode: String): io.soo.springboot.storage.db.core.Service {
        val normalizedCode = serviceContextResolver.normalizeAndValidate(serviceCode)
        val current = serviceRepository.findByServiceCode(normalizedCode)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("serviceCode" to normalizedCode))

        if (current.serviceCode == DEFAULT_SERVICE_CODE) {
            throw CoreException(
                ErrorType.CONFLICT,
                data = mapOf("serviceCode" to normalizedCode, "reason" to "DEFAULT_SERVICE_PROTECTED")
            )
        }

        if (current.serviceStatus == ServiceStatus.INACTIVE) return current

        val activeMembershipCount = serviceMembershipRepository.countActiveMemberships(current.id)
        if (activeMembershipCount > 0) {
            throw CoreException(
                ErrorType.SERVICE_HAS_ACTIVE_MEMBERSHIPS,
                data = mapOf("serviceCode" to normalizedCode, "activeMemberships" to activeMembershipCount),
            )
        }

        return serviceRepository.save(current.copy(serviceStatus = ServiceStatus.INACTIVE))
    }
}