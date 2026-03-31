package io.soo.springboot.core.domain.local.support

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom

data class AccountCredentials(
    val email: String,
    val rawPassword: String,
)

@Component
class PhoneAccountFactory(
    private val userRepository: UserRepository,
) {
    private val secureRandom = SecureRandom()

    fun create(normalizedPhone: String): AccountCredentials {
        return AccountCredentials(
            email = issueUniqueInternalEmail(normalizedPhone),
            rawPassword = generateInternalPassword(),
        )
    }

    private fun issueUniqueInternalEmail(normalizedPhone: String): String {
        var candidate = buildInternalEmail(normalizedPhone)
        var attempt = 0
        while (userRepository.existsByEmail(candidate) && attempt < 5) {
            attempt += 1
            candidate = buildInternalEmail("$normalizedPhone#$attempt")
        }
        if (userRepository.existsByEmail(candidate)) {
            throw CoreException(
                ErrorType.INVALID_REQUEST,
                data = mapOf("reason" to "failed to allocate internal email"),
            )
        }
        return candidate
    }

    private fun buildInternalEmail(seed: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val hash = digestBytes.joinToString("") { "%02x".format(it) }.take(40)
        return "phone-$hash@local.internal"
    }

    private fun generateInternalPassword(length: Int = 32): String {
        val lower = "abcdefghjkmnpqrstuvwxyz"
        val upper = "ABCDEFGHJKMNPQRSTUVWXYZ"
        val digits = "23456789"
        val symbols = "!@#$%^&*()-_=+[]{}"

        val required = mutableListOf(
            lower.random(secureRandom),
            upper.random(secureRandom),
            digits.random(secureRandom),
            symbols.random(secureRandom),
        )

        val all = lower + upper + digits + symbols
        repeat((length - required.size).coerceAtLeast(0)) {
            required += all.random(secureRandom)
        }
        for (i in required.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = required[i]
            required[i] = required[j]
            required[j] = tmp
        }
        return required.joinToString("")
    }

    private fun String.random(random: SecureRandom): Char = this[random.nextInt(this.length)]
}
