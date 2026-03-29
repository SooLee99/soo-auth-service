package io.soo.springboot.core.domain.local.phone

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PhoneVerifyServiceTest {
    private val store = InMemPhoneStore()
    private val notifier = CapturingNotifier()
    private val service = PhoneVerifyService(store, notifier)

    @Test
    fun `인증 요청 후 확인 성공 시 증명 토큰 발급`() {
        val issued = service.issue("+82 10-1234-5678")
        val code = notifier.lastCode ?: error("code not sent")

        val confirmed = service.confirm(
            phoneNumber = "+82 10-1234-5678",
            verificationId = issued.verificationId,
            code = code,
        )

        service.consume("+82 10-1234-5678", confirmed.proofToken)
    }

    @Test
    fun `잘못된 인증번호면 실패`() {
        val issued = service.issue("010-1234-5678")

        val ex = assertThrows(CoreException::class.java) {
            service.confirm(
                phoneNumber = "010-1234-5678",
                verificationId = issued.verificationId,
                code = "000000",
            )
        }

        assertEquals(ErrorType.INVALID_PHONE_VERIFICATION_CODE, ex.errorType)
    }

    private class CapturingNotifier : PhoneNotifier {
        var lastCode: String? = null

        override fun sendCode(phoneNumber: String, code: String, expiresInSec: Long) {
            lastCode = code
        }
    }
}
