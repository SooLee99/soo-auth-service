package io.soo.springboot.core.domain

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.security.crypto.password.PasswordEncoder

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.UserEntity
import io.soo.springboot.storage.db.core.UseRepository

@Service
class LocalAccountService (
    private val accountRepository: UseRepository,
    private val passwordEncoder: PasswordEncoder
){
    @Transactional
    fun signUp(
        email: String,
        password: String,
        nickname: String?,
        name: String?,
        locale: String,
        gender: Gender,
        phoneNumber: String,
        profileImageUrl: String?,
        thumbnailImageUrl: String?,
        birthyear: String?,
        birthday: String?,
    ): UserEntity? {
        require(!accountRepository.existsByEmail(email)) { "이미 존재하는 이메일입니다." }
        require(!accountRepository.existsByPhoneNumber(phoneNumber)) { "이미 존재하는 전화번호입니다." }

        // TODO: 비밀번호 암호화 + 자격 증명 테이블 추가로 생성 및 저장 (추후 로그인이랑 연계 예정)


        return accountRepository.save(
            UserEntity(
                email = email,
                emailVerified = false,
                phoneNumber = phoneNumber,
                phoneVerified = false,
                name = name,
                nickname = nickname,
                gender = gender,
                locale = locale,
                birthyear = birthyear,
                birthday = birthday,
                profileImageUrl = profileImageUrl,
                thumbnailImageUrl = thumbnailImageUrl,
            ),
        )
    }
}
