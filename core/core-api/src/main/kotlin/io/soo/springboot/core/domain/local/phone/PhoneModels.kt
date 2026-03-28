package io.soo.springboot.core.domain.local.phone

import java.time.Instant

data class PhoneChallenge(
    val verificationId: String,
    val phoneNumber: String,
    val code: String,
    val expiresAt: Instant,
)

data class PhoneProof(
    val proofToken: String,
    val phoneNumber: String,
    val expiresAt: Instant,
)

data class PhoneIssue(
    val verificationId: String,
    val expiresInSec: Long,
)

data class PhoneConfirm(
    val proofToken: String,
    val expiresInSec: Long,
)
