package io.soo.springboot.core.domain.phone.verification

interface PhoneVerificationNotifier {
    fun sendCode(phoneNumber: String, code: String, expiresInSec: Long)
}
