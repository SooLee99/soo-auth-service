package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.LoginDenyReason
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import io.soo.springboot.storage.db.core.User
import org.springframework.stereotype.Component

@Component
class UserStatusPolicy {

    /**
     * 상태 우선순위:
     * 1) SOFT_DELETED (항상 로그인 불가)
     * 2) BLOCKED (로그인 불가)
     * 3) ACTIVE (로그인 가능)
     */
    fun validateLoginAllowed(user: User) {
        if (user.userStatus == UserStatus.SOFT_DELETED) {
            throw AccountStatusDeniedException(
                reason = LoginDenyReason.SOFT_DELETED,
                detailReason = user.deletionReason,
                at = user.deletedAt,
                actorAdminId = null,
            )
        }

        if (user.userStatus == UserStatus.BLOCKED || user.blocked) {
            throw AccountStatusDeniedException(
                reason = LoginDenyReason.BLOCKED,
                detailReason = user.blockedReason,
                at = user.blockedAt,
                actorAdminId = user.blockedByAdminId,
            )
        }
    }
}
