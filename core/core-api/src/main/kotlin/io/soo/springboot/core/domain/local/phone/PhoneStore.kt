package io.soo.springboot.core.domain.local.phone

interface PhoneStore {
    fun saveChallenge(challenge: PhoneChallenge)
    fun findChallenge(verificationId: String): PhoneChallenge?
    fun deleteChallenge(verificationId: String)

    fun saveProof(proof: PhoneProof)
    fun findProof(proofToken: String): PhoneProof?
    fun deleteProof(proofToken: String)
}
