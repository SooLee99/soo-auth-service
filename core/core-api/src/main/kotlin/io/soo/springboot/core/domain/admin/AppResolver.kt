package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.Service
import io.soo.springboot.storage.db.core.ServiceRepository
import org.springframework.stereotype.Component

@Component
class AppResolver(
    private val serviceRepository: ServiceRepository,
) {
    companion object {
        private val SERVICE_CODE_REGEX = Regex("^[A-Z0-9][A-Z0-9_-]{1,63}$")
    }

    fun active(serviceCode: String): Service {
        val normalizedCode = normalize(serviceCode)
        val active = serviceRepository.findActiveByServiceCode(normalizedCode)
        if (active != null) return active

        val existing = serviceRepository.findByServiceCode(normalizedCode)
        if (existing != null) {
            throw CoreException(ErrorType.SERVICE_INACTIVE, data = mapOf("serviceCode" to normalizedCode))
        }

        throw CoreException(ErrorType.NOT_FOUND, data = mapOf("serviceCode" to normalizedCode))
    }

    fun normalize(serviceCode: String): String {
        val normalized = serviceCode.trim().uppercase()
        if (!SERVICE_CODE_REGEX.matches(normalized)) {
            throw CoreException(
                ErrorType.INVALID_PARAMETER,
                data = mapOf("serviceCode" to serviceCode, "rule" to "^[A-Z0-9][A-Z0-9_-]{1,63}$"),
            )
        }
        return normalized
    }
}