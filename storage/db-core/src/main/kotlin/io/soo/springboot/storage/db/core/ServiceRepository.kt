package io.soo.springboot.storage.db.core

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ServiceRepository {
    fun save(service: Service): Service
    fun findById(id: Long): Service?
    fun findByServiceCode(serviceCode: String): Service?
    fun findActiveByServiceCode(serviceCode: String): Service?
    fun findActive(pageable: Pageable): Page<Service>
}

