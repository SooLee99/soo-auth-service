package io.soo.springboot.core.domain.phone.verification

import org.springframework.stereotype.Component

@Component
class PhoneNumberNormalizer {
    fun normalize(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()
        val hasPlusPrefix = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlusPrefix) "+$digits" else digits
    }
}
