package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.UserEntity
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.LocalCredentialEntity
import io.soo.springboot.storage.db.core.LocalCredentialRepository

@Service
class LocalAccountService(
    private val userRepository: UserRepository,
    private val localAccountRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun signUp(cmd: LocalSignUpCommand): UserEntity {
        if (userRepository.existsByEmail(cmd.email)) {
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to cmd.email))
        }
        if (userRepository.existsByPhoneNumber(cmd.phoneNumber)) {
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to cmd.phoneNumber))
        }
        val user = userRepository.save(
            UserEntity(
                email = cmd.email,
                emailVerified = false,
                phoneNumber = cmd.phoneNumber,
                phoneVerified = false,
                name = cmd.name,
                nickname = cmd.nickname,
                gender = cmd.gender,
                locale = cmd.locale,
                birthyear = cmd.birthyear,
                birthday = cmd.birthday,
                profileImageUrl = cmd.profileImageUrl,
                thumbnailImageUrl = cmd.thumbnailImageUrl,
                authProvider = AuthProvider.LOCAL,
            )
        )
        localAccountRepository.save(
            LocalCredentialEntity(
                userId = requireNotNull(user.id) { "사용자 정보를 저장하지 못했습니다." },
                passwordHash = passwordEncoder.encode(cmd.password),
            )
        )

        return user
    }
}
