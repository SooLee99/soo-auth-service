package io.soo.springboot.storage.db.core

data class LocalCredential(
    val id: Long = 0L,
    val userId: Long,
    val userEmail: String,
    val passwordHash: String,
)