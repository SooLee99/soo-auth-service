package io.soo.springboot.core.domain

import org.springframework.security.authentication.LockedException
import java.time.Instant

class AccountStatusDeniedException(
    val reason: LoginDenyReason,
    val detailReason: String?,
    val at: Instant?,
    val actorAdminId: Long?,
) : LockedException("Login denied by user status")
