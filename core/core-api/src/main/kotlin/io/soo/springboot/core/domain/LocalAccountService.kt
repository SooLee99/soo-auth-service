package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.UserAccountRepository
import org.springframework.stereotype.Service

@Service
class LocalAccountService (
    private val accountRepository: UserAccountRepository,
){
    fun signUp(
        email: String,
        password: String,
        name: String?,
        gender: Gender,
        nickname: String?,
        profileImageUrl: String?,
        thumbnailImageUrl: String?,
        birthyear: String?,
        birthday: String?,
    ) {
        // TODO: 이메일 중복 여부 확인

        // TODO: 전화번호 중복 여부 확인

        // TODO: 회원 정보 저장

    }

}
