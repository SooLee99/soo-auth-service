package io.soo.springboot.core.domain.local.phone

interface PhoneNotifier {
    fun sendCode(phoneNumber: String, code: String, expiresInSec: Long)
}
