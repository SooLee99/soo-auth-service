package io.soo.springboot.core.domain.phone.verification

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PhoneVerificationServiceTest {
    private val store = InMemoryPhoneVerificationStore()
    private val notifier = CapturingNotifier()
    private val service = PhoneVerificationService(store, notifier)

    @Test
    fun `인증 요청 후 확인 성공 시 증명 토큰 발급`() {
        val issued = service.createVerification("+82 10-1234-5678")
        val code = notifier.lastCode ?: error("code not sent")

        val confirmed = service.confirmVerification(
            phoneNumber = "+82 10-1234-5678",
            verificationId = issued.verificationId,
            code = code,
        )

        service.consumeVerificationToken("+82 10-1234-5678", confirmed.proofToken)
    }

    @Test
    fun `잘못된 인증번호면 실패`() {
        val issued = service.createVerification("010-1234-5678")

        val ex = assertThrows(CoreException::class.java) {
            service.confirmVerification(
                phoneNumber = "010-1234-5678",
                verificationId = issued.verificationId,
                code = "000000",
            )
        }

        assertEquals(ErrorType.INVALID_PHONE_VERIFICATION_CODE, ex.errorType)
    }

    private class CapturingNotifier : PhoneVerificationNotifier {
        var lastCode: String? = null

        override fun sendCode(phoneNumber: String, code: String, expiresInSec: Long) {
            lastCode = code
        }
    }
}
