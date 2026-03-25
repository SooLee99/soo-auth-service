package io.soo.springboot.core.domain.local.phone

interface PhoneVerificationNotifier {
    fun sendVerificationCode(phoneNumber: String, code: String, expiresInSec: Long)
}
