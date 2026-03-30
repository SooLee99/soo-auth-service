package io.soo.springboot.core.domain.local.phone

interface PhoneVerificationStore {
    fun saveChallenge(challenge: PhoneVerificationChallenge)
    fun findChallenge(verificationId: String): PhoneVerificationChallenge?
    fun deleteChallenge(verificationId: String)

    fun saveProof(proof: PhoneVerificationProof)
    fun findProof(proofToken: String): PhoneVerificationProof?
    fun deleteProof(proofToken: String)
}
