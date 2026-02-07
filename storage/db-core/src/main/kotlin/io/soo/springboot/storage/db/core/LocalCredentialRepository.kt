package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface LocalCredentialRepository : JpaRepository<LocalCredentialEntity, Long>, JpaSpecificationExecutor<LocalCredentialEntity>
