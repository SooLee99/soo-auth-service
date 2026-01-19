package io.soo.springboot.core.domain

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

import io.soo.springboot.core.api.controller.v1.request.AdminUpdateUserRequest
import io.soo.springboot.storage.db.core.UserAccountRepository

@Service
class AdminUserCommandService(
    private val userAccountRepository: UserAccountRepository,
) {

    @Transactional
    fun suspendUser(userId: Long, reason: String, until: LocalDateTime?) {
        val user = userAccountRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found: $userId") }

        val now = LocalDateTime.now()
        if (until != null && !until.isAfter(now)) {
            throw IllegalArgumentException("until은 현재 시각 이후여야 합니다.")
        }

        user.suspendedAt = now
        user.suspendedUntil = until
        user.suspendedReason = reason.trim()

        userAccountRepository.save(user)
    }

    @Transactional
    fun unsuspendUser(userId: Long) {
        val user = userAccountRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found: $userId") }

        user.suspendedAt = null
        user.suspendedUntil = null
        user.suspendedReason = null

        userAccountRepository.save(user)
    }

    @Transactional
    fun updateUserProfile(userId: Long, req: AdminUpdateUserRequest) {
        val user = userAccountRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found: $userId") }

        // null이 아니면 덮어쓰기(부분 업데이트)
        if (req.name != null) user.name = req.name
        if (req.nickname != null) user.nickname = req.nickname
        if (req.profileImageUrl != null) user.profileImageUrl = req.profileImageUrl
        if (req.thumbnailImageUrl != null) user.thumbnailImageUrl = req.thumbnailImageUrl
        if (req.birthDate != null) user.birthdate = req.birthDate
        if (req.emailVerified != null) user.emailVerified = req.emailVerified

        userAccountRepository.save(user)
    }
}
