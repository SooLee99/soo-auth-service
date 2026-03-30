package io.soo.springboot.core.domain.local.phone

interface PhoneVerificationNotifier {
    fun sendCode(phoneNumber: String, code: String, expiresInSec: Long)
}
