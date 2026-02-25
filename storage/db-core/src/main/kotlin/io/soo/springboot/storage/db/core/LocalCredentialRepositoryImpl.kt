package io.soo.springboot.storage.db.core

import org.springframework.stereotype.Repository

@Repository
class LocalCredentialRepositoryImpl(
    private val jpaRepository: LocalCredentialJpaRepository
) : LocalCredentialRepository {

    override fun save(credential: LocalCredential): LocalCredential {
        val entity = credential.toEntity()
        val saved = jpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findByUserId(userId: Long): LocalCredential? {
        return jpaRepository.findByUserId(userId)?.toModel()
    }

    override fun findByUserEmail(email: String): LocalCredential? {
        return jpaRepository.findByUserEmail(email)?.toModel()
    }

    private fun LocalCredential.toEntity(): LocalCredentialEntity {
        return LocalCredentialEntity(
            userId = userId,
            userEmail = userEmail,
            passwordHash = passwordHash,
        ).also {
            if (id > 0) it.id = id
        }
    }

    private fun LocalCredentialEntity.toModel(): LocalCredential {
        return LocalCredential(
            id = id,
            userId = userId,
            userEmail = userEmail,
            passwordHash = passwordHash,
        )
    }
}