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
    val id: Long = 0

    @Version
    @Column(nullable = false)
    var version: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR")
    private var entityStatus: EntityStatus = EntityStatus.ACTIVE

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    @Column(name = "deleted_reason", length = 500)
    var deletedReason: String? = null
        protected set

    fun softDelete(reason: String? = null, at: LocalDateTime = LocalDateTime.now()) {
        entityStatus = EntityStatus.DELETED
        deletedAt = at
        deletedReason = reason
    }

    fun active() {
        entityStatus = EntityStatus.ACTIVE
    }

    fun delete() {
        entityStatus = EntityStatus.DELETED
    }

    fun isActive(): Boolean = entityStatus == EntityStatus.ACTIVE
    fun isDeleted(): Boolean = entityStatus == EntityStatus.DELETED

    fun getEntityStatus(): EntityStatus = entityStatus
}
