package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.ServiceStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "service_entity",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_service_code", columnNames = ["serviceCode"]),
    ],
    indexes = [
        Index(name = "ix_service_status", columnList = "serviceStatus"),
    ],
)
class ServiceEntity(
    @Column(nullable = false, length = 64)
    var serviceCode: String,

    @Column(nullable = false, length = 120)
    var serviceName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var serviceStatus: ServiceStatus = ServiceStatus.ACTIVE,
) : BaseEntity()

