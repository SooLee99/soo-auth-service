package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.ServiceStatus
import io.soo.springboot.storage.db.core.Service

data class AdminServiceResponse(
    val serviceId: Long,
    val serviceCode: String,
    val serviceName: String,
    val serviceStatus: ServiceStatus,
) {
    companion object {
        fun from(service: Service): AdminServiceResponse {
            return AdminServiceResponse(
                serviceId = service.id,
                serviceCode = service.serviceCode,
                serviceName = service.serviceName,
                serviceStatus = service.serviceStatus,
            )
        }
    }
}

