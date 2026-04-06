package io.soo.springboot.core.domain.local

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.stereotype.Component

@Component
class UserUniquenessPolicy(
    private val userRepository: UserRepository,
) {
    fun validateSignUp(email: String, rawPhone: String?, normalizedPhone: String?) {
        validateEmailAvailable(email)
        if (!rawPhone.isNullOrBlank() && !normalizedPhone.isNullOrBlank()) {
            validatePhoneAvailable(rawPhone, normalizedPhone)
        }
    }

    fun validateLoginIdAvailable(loginId: String) {
        if (userRepository.existsByLoginId(loginId)) {
            throw CoreException(ErrorType.DUPLICATE_LOGIN_ID, data = mapOf("loginId" to loginId))
        }
    }

    fun validatePhoneAvailable(rawPhone: String, normalizedPhone: String) {
        val duplicated = userRepository.existsByPhoneNumber(rawPhone) ||
            (normalizedPhone != rawPhone && userRepository.existsByPhoneNumber(normalizedPhone))
        if (duplicated) {
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to rawPhone))
        }
    }

    fun validateUpdateEmail(currentUserId: Long, currentEmail: String, targetEmail: String) {
        if (targetEmail == currentEmail) return
        val existed = userRepository.findByEmail(targetEmail)
        if (existed != null && existed.id != currentUserId) {
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to targetEmail))
        }
    }

    fun validateUpdatePhone(currentUserId: Long, currentPhone: String?, targetPhone: String?) {
        if (currentUserId <= 0) return
        if (targetPhone == null || targetPhone == currentPhone) return
        val duplicated = userRepository.existsByPhoneNumber(targetPhone)
        if (duplicated) {
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to targetPhone))
        }
    }

    private fun validateEmailAvailable(email: String) {
        if (userRepository.existsByEmail(email)) {
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to email))
        }
    }
}
