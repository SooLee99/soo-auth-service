package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.UserAccountEntity
import io.soo.springboot.storage.db.core.UserAccountRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class LocalAccountService (
    private val accountRepository: UserAccountRepository,
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
    ): UserAccountEntity? {
        if (accountRepository.findByEmail(email) != null) {
            throw IllegalArgumentException("이미 존재하는 이메일입니다.")
        }
        if (accountRepository.findByPhoneNumber(phoneNumber) != null) {
            throw IllegalArgumentException("이미 존재하는 전화번호입니다.")
        }

        // TODO: 비밀번호 암호화 + 자격 증명 테이블 추가로 생성 및 저장 (추후 로그인이랑 연계 예정)

        return accountRepository.save(
            UserAccountEntity(
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
