package io.soo.springboot.core.domain.local.phone

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryPhoneVerificationStore : PhoneVerificationStore {
    private val challenges = ConcurrentHashMap<String, PhoneVerificationChallenge>()
    private val proofs = ConcurrentHashMap<String, PhoneVerificationProof>()

    override fun saveChallenge(challenge: PhoneVerificationChallenge) {
        challenges[challenge.verificationId] = challenge
    }

    override fun findChallenge(verificationId: String): PhoneVerificationChallenge? {
        val challenge = challenges[verificationId] ?: return null
        if (challenge.expiresAt.isBefore(Instant.now())) {
            challenges.remove(verificationId)
            return null
        }
        return challenge
    }

    override fun deleteChallenge(verificationId: String) {
        challenges.remove(verificationId)
    }

    override fun saveProof(proof: PhoneVerificationProof) {
        proofs[proof.proofToken] = proof
    }

    override fun findProof(proofToken: String): PhoneVerificationProof? {
        val proof = proofs[proofToken] ?: return null
        if (proof.expiresAt.isBefore(Instant.now())) {
            proofs.remove(proofToken)
            return null
        }
        return proof
    }

    override fun deleteProof(proofToken: String) {
        proofs.remove(proofToken)
    }
}
