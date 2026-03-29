package io.soo.springboot.core.domain.local.phone

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class PhoneVerifyService(
    private val store: PhoneStore,
    private val notifier: PhoneNotifier,
) {
    private val random = SecureRandom()

    companion object {
        private val CHALLENGE_TTL: Duration = Duration.ofMinutes(3)
        private val PROOF_TTL: Duration = Duration.ofMinutes(10)
    }

    fun issue(phoneNumber: String): PhoneIssue {
        val normalizedPhone = normalizePhone(phoneNumber)
        val verificationId = UUID.randomUUID().toString()
        val code = generateCode()
        val expiresAt = Instant.now().plus(CHALLENGE_TTL)

        store.saveChallenge(
            PhoneChallenge(
                verificationId = verificationId,
                phoneNumber = normalizedPhone,
                code = code,
                expiresAt = expiresAt,
            )
        )

        notifier.sendCode(normalizedPhone, code, CHALLENGE_TTL.seconds)

        return PhoneIssue(
            verificationId = verificationId,
            expiresInSec = CHALLENGE_TTL.seconds,
        )
    }

    fun confirm(phoneNumber: String, verificationId: String, code: String): PhoneConfirm {
        val normalizedPhone = normalizePhone(phoneNumber)
        val challenge = store.findChallenge(verificationId)
            ?: throw CoreException(ErrorType.INVALID_PHONE_VERIFICATION)

        if (challenge.phoneNumber != normalizedPhone) {
            throw CoreException(ErrorType.INVALID_PHONE_VERIFICATION)
        }
        if (challenge.code != code.trim()) {
            throw CoreException(ErrorType.INVALID_PHONE_VERIFICATION_CODE)
        }
        if (challenge.expiresAt.isBefore(Instant.now())) {
            store.deleteChallenge(verificationId)
            throw CoreException(ErrorType.EXPIRED_PHONE_VERIFICATION)
        }

        store.deleteChallenge(verificationId)

        val proofToken = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(PROOF_TTL)
        store.saveProof(
            PhoneProof(
                proofToken = proofToken,
                phoneNumber = normalizedPhone,
                expiresAt = expiresAt,
            )
        )

        return PhoneConfirm(
            proofToken = proofToken,
            expiresInSec = PROOF_TTL.seconds,
        )
    }

    fun consume(phoneNumber: String, proofToken: String) {
        val normalizedPhone = normalizePhone(phoneNumber)
        val proof = store.findProof(proofToken.trim())
            ?: throw CoreException(ErrorType.PHONE_VERIFICATION_REQUIRED)

        if (proof.phoneNumber != normalizedPhone) {
            throw CoreException(ErrorType.PHONE_VERIFICATION_REQUIRED)
        }
        if (proof.expiresAt.isBefore(Instant.now())) {
            store.deleteProof(proofToken)
            throw CoreException(ErrorType.EXPIRED_PHONE_VERIFICATION)
        }

        store.deleteProof(proofToken)
    }

    private fun generateCode(): String {
        return (100000 + random.nextInt(900000)).toString()
    }

    private fun normalizePhone(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()
        val hasPlusPrefix = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlusPrefix) "+$digits" else digits
    }
}
