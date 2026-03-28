package io.soo.springboot.core.domain.local.phone

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryPhoneStore : PhoneStore {
    private val challenges = ConcurrentHashMap<String, PhoneChallenge>()
    private val proofs = ConcurrentHashMap<String, PhoneProof>()

    override fun saveChallenge(challenge: PhoneChallenge) {
        challenges[challenge.verificationId] = challenge
    }

    override fun findChallenge(verificationId: String): PhoneChallenge? {
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

    override fun saveProof(proof: PhoneProof) {
        proofs[proof.proofToken] = proof
    }

    override fun findProof(proofToken: String): PhoneProof? {
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
