package io.soo.springboot.core.domain.local.phone

import java.time.Instant

data class PhoneVerificationChallenge(
    val verificationId: String,
    val phoneNumber: String,
    val code: String,
    val expiresAt: Instant,
)

data class PhoneVerificationProof(
    val proofToken: String,
    val phoneNumber: String,
    val expiresAt: Instant,
)

data class PhoneVerificationIssueResult(
    val verificationId: String,
    val expiresInSec: Long,
)

data class PhoneVerificationConfirmResult(
    val proofToken: String,
    val expiresInSec: Long,
)
