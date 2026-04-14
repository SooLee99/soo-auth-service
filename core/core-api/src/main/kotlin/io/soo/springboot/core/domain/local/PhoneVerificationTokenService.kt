package io.soo.springboot.core.domain.local

import io.soo.springboot.core.domain.phone.verification.PhoneNumberNormalizer
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationService
import org.springframework.stereotype.Service

interface PhoneVerificationTokenService {
    fun consumeVerifiedToken(phoneNumber: String, proofToken: String)
    fun normalize(phoneNumber: String): String
}

@Service
class DefaultPhoneVerificationTokenService(
    private val phoneVerificationService: PhoneVerificationService,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
) : PhoneVerificationTokenService {
    override fun consumeVerifiedToken(phoneNumber: String, proofToken: String) {
        phoneVerificationService.consumeVerificationToken(phoneNumber, proofToken)
    }

    override fun normalize(phoneNumber: String): String {
        return phoneNumberNormalizer.normalize(phoneNumber)
    }
}
