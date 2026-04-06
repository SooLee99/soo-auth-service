package io.soo.springboot.core.domain.phone.verification

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
@Profile("local-dev", "dev", "staging", "live")
class RedisPhoneVerificationStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : PhoneVerificationStore {
    companion object {
        private const val CHALLENGE_KEY = "auth:phone:challenge:"
        private const val PROOF_KEY = "auth:phone:proof:"
    }

    override fun saveChallenge(challenge: PhoneVerificationChallenge) {
        val ttl = Duration.between(Instant.now(), challenge.expiresAt).coerceAtLeast(Duration.ofSeconds(1))
        redis.opsForValue().set(
            "$CHALLENGE_KEY${challenge.verificationId}",
            objectMapper.writeValueAsString(challenge),
            ttl,
        )
    }

    override fun findChallenge(verificationId: String): PhoneVerificationChallenge? {
        val raw = redis.opsForValue().get("$CHALLENGE_KEY$verificationId") ?: return null
        val challenge = objectMapper.readValue(raw, PhoneVerificationChallenge::class.java)
        if (challenge.expiresAt.isBefore(Instant.now())) {
            deleteChallenge(verificationId)
            return null
        }
        return challenge
    }

    override fun deleteChallenge(verificationId: String) {
        redis.delete("$CHALLENGE_KEY$verificationId")
    }

    override fun saveProof(proof: PhoneVerificationProof) {
        val ttl = Duration.between(Instant.now(), proof.expiresAt).coerceAtLeast(Duration.ofSeconds(1))
        redis.opsForValue().set(
            "$PROOF_KEY${proof.proofToken}",
            objectMapper.writeValueAsString(proof),
            ttl,
        )
    }

    override fun findProof(proofToken: String): PhoneVerificationProof? {
        val raw = redis.opsForValue().get("$PROOF_KEY$proofToken") ?: return null
        val proof = objectMapper.readValue(raw, PhoneVerificationProof::class.java)
        if (proof.expiresAt.isBefore(Instant.now())) {
            deleteProof(proofToken)
            return null
        }
        return proof
    }

    override fun deleteProof(proofToken: String) {
        redis.delete("$PROOF_KEY$proofToken")
    }
}
