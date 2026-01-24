package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface VerificationGuardRepository : JpaRepository<VerificationGuardEntity, String>
