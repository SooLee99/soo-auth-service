package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType

import io.soo.springboot.storage.db.core.UserEntity
import io.soo.springboot.storage.db.core.UserJpaRepository
import io.soo.springboot.storage.db.core.LocalCredentialEntity
import io.soo.springboot.storage.db.core.LocalCredentialJpaRepository

@Service
class LocalAccountService(
    private val userJpaRepository: UserJpaRepository,
    private val localAccountRepository: LocalCredentialJpaRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun signUp(cmd: LocalSignUpCommand): UserEntity {
        // 1) 중복 이메일/전화번호 검사
        if (userJpaRepository.existsByEmail(cmd.email))
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to cmd.email))
        if (userJpaRepository.existsByPhoneNumber(cmd.phoneNumber))
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to cmd.phoneNumber))

        // 2) 사용자 정보 저장
        val user = userJpaRepository.save(
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

        // 3) 로컬 자격증명 저장
        localAccountRepository.save(
            LocalCredentialEntity(
                userId = user.id,
                userEmail = cmd.email,
                passwordHash = passwordEncoder.encode(cmd.password),
            )
        )
        return user
    }
}
