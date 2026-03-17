package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceStatus

data class Service(
    val id: Long = 0L,
    val serviceCode: String,
    val serviceName: String,
    val serviceStatus: ServiceStatus = ServiceStatus.ACTIVE,
)

