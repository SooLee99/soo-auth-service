package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.EntityStatus
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Version
    @Column(nullable = false)
    var version: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_status", nullable = false)
    private var status: EntityStatus = EntityStatus.ACTIVE

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    fun active() {
        status = EntityStatus.ACTIVE
    }

    fun isActive(): Boolean {
        return status == EntityStatus.ACTIVE
    }

    fun delete() {
        status = EntityStatus.DELETED
    }

    fun isDeleted(): Boolean {
        return status == EntityStatus.DELETED
    }
}
