package io.soo.springboot.core.domain

import io.soo.springboot.core.api.controller.v1.response.VerificationCodeConfirmResponse
import io.soo.springboot.core.api.controller.v1.response.VerificationCodeResponse
import io.soo.springboot.core.enums.VerificationChannel
import io.soo.springboot.core.enums.VerificationStatus
import io.soo.springboot.storage.db.core.UserAccountRepository
import io.soo.springboot.storage.db.core.VerificationGuardEntity
import io.soo.springboot.storage.db.core.VerificationGuardRepository
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest
import org.springframework.security.authentication.ott.OneTimeTokenService
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.random.Random

@Service
class VerificationService(
    private val oneTimeTokenService: OneTimeTokenService,
    private val guardRepo: VerificationGuardRepository,
    private val userAccountRepository: UserAccountRepository,
    private val smsSender: SmsSender,
    private val emailSender: EmailSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val COOLDOWN: Duration = Duration.ofSeconds(60)
        private val TOKEN_TTL: Duration = Duration.ofMinutes(5)
        private const val MAX_VERIFY_ATTEMPTS = 5
        private val LOCK_DURATION: Duration = Duration.ofMinutes(10)

        private val SEND_WINDOW: Duration = Duration.ofHours(1)
        private const val MAX_SEND_PER_WINDOW = 5

        // 토큰 username에 목적/채널/식별자를 묶어 스코프 분리
        private const val PURPOSE_PREFIX = "SIGNUP_VERIFY"
    }

    private fun now(): Instant = Instant.now()

    private fun normalizeIdentifier(raw: String): String =
        raw.trim().lowercase()

    private fun isValidIdentifier(channel: VerificationChannel, id: String): Boolean {
        if (id.isBlank()) return false
        return when (channel) {
            VerificationChannel.EMAIL -> id.contains("@") && id.length <= 200
            VerificationChannel.SMS -> id.length in 8..20 // 프로젝트 규칙에 맞게 조정
        }
    }

    private fun scopedUsername(channel: VerificationChannel, identifier: String): String =
        "$PURPOSE_PREFIX:${channel.name}:${identifier}"

    private fun guardKey(channel: VerificationChannel, identifier: String): String =
        scopedUsername(channel, identifier)

    private fun secondsUntil(t: Instant): Long =
        max(0, Duration.between(now(), t).seconds)

    /**
     * ✅ 인증코드 발송/재발송
     * - 계정 존재 여부와 무관하게 항상 200으로 “처리된 것처럼” 응답
     * - 쿨다운/시간창 제한/락이면 실제 발송은 안 함
     */
    @Transactional
    fun requestCode(channel: VerificationChannel, rawIdentifier: String): VerificationCodeResponse {
        val identifier = normalizeIdentifier(rawIdentifier)

        if (!isValidIdentifier(channel, identifier)) {
            return VerificationCodeResponse(VerificationStatus.INVALID_FORMAT)
        }

        val key = guardKey(channel, identifier)
        val g = guardRepo.findById(key).orElseGet { VerificationGuardEntity(key = key) }

        // 1) 잠금
        g.lockedUntil?.let { until ->
            if (until.isAfter(now())) {
                guardRepo.save(g)
                return VerificationCodeResponse(
                    status = VerificationStatus.LOCKED,
                    retryAfterSeconds = secondsUntil(until),
                )
            }
        }

        // 2) (선택) 시간창 기준 전송 횟수 제한
        val n = now()
        if (g.windowStart == null || Duration.between(g.windowStart, n) > SEND_WINDOW) {
            g.windowStart = n
            g.windowCount = 0
        }
        if (g.windowCount >= MAX_SEND_PER_WINDOW) {
            val windowEnd = g.windowStart!!.plus(SEND_WINDOW)
            guardRepo.save(g)
            return VerificationCodeResponse(
                status = VerificationStatus.COOLDOWN,
                retryAfterSeconds = secondsUntil(windowEnd),
            )
        }

        // 3) 쿨다운
        g.lastSentAt?.let { last ->
            val next = last.plus(COOLDOWN)
            if (next.isAfter(n)) {
                guardRepo.save(g)
                return VerificationCodeResponse(
                    status = VerificationStatus.COOLDOWN,
                    retryAfterSeconds = secondsUntil(next),
                )
            }
        }

        // 4) 실제 발송 시도 (계정열거 방지: “없으면” 조용히 스킵)
        val userExists = when (channel) {
            VerificationChannel.EMAIL -> userAccountRepository.findByEmail(identifier) != null
            VerificationChannel.SMS -> userAccountRepository.findByPhoneNumber(identifier) != null
        }

        if (userExists) {
            val usernameScoped = scopedUsername(channel, identifier)

            val token = oneTimeTokenService.generate(
                GenerateOneTimeTokenRequest(usernameScoped)
            )

            try {
                when (channel) {
                    VerificationChannel.EMAIL ->
                        emailSender.send(identifier, "가입 인증 코드", "인증 코드: ${token.tokenValue}")
                    VerificationChannel.SMS ->
                        smsSender.send(identifier, "인증 코드: ${token.tokenValue}")
                }
            } catch (e: Exception) {
                // 요구사항(항상 200 + 열거방지) 때문에, 발송 실패도 외부로는 숨김
                log.warn("verification send failed. channel=$channel identifier=$identifier", e)
            }
        }

        // 계정이 없어도 guard 상태는 동일하게 업데이트(열거 방지)
        g.lastSentAt = n
        g.windowCount += 1
        guardRepo.save(g)

        return VerificationCodeResponse(
            status = VerificationStatus.SENT,
            retryAfterSeconds = COOLDOWN.seconds,
        )
    }

    /**
     * ✅ 인증코드 확인
     * - tokenValue(=code)로 consume (Spring Security 7+에서 token-only 생성자가 권장됨) :contentReference[oaicite:1]{index=1}
     * - 성공이면 token.username(=scopedUsername)에서 채널/식별자를 복원해 verified 처리
     * - 실패면 요청 식별자 기준으로 failedAttempts 증가 + 잠금
     * - 모든 경우 200 응답 패턴(컨트롤러에서 보장)
     */
    @Transactional
    fun confirmCode(
        channel: VerificationChannel,
        rawIdentifier: String,
        rawCode: String,
    ): VerificationCodeConfirmResponse {
        val identifier = normalizeIdentifier(rawIdentifier)
        val code = rawCode.trim()

        if (!isValidIdentifier(channel, identifier) || code.isBlank()) {
            return VerificationCodeConfirmResponse(
                verified = false,
                status = VerificationStatus.INVALID,
            )
        }

        // 응답시간 지터로 타이밍 기반 추측 완화
        Thread.sleep(Random.nextLong(10, 40))

        // ✅ consume: 유효하면 OneTimeToken, 아니면 null :contentReference[oaicite:2]{index=2}
        val consumed = oneTimeTokenService.consume(OneTimeTokenAuthenticationToken(code))

        if (consumed != null) {
            // 예: SIGNUP_VERIFY:EMAIL:a@b.com
            val parts = consumed.username.split(":", limit = 3)
            if (parts.size == 3 && parts[0] == PURPOSE_PREFIX) {
                val tokenChannel = runCatching { VerificationChannel.valueOf(parts[1]) }.getOrNull()
                val tokenIdentifier = parts[2]

                if (tokenChannel != null) {
                    // ✅ verified 처리 (계정이 없으면 조용히 스킵)
                    when (tokenChannel) {
                        VerificationChannel.EMAIL -> {
                            val user = userAccountRepository.findByEmail(tokenIdentifier)
                            if (user != null) {
                                user.emailVerified = true
                                userAccountRepository.save(user)
                            }
                        }
                        VerificationChannel.SMS -> {
                            val user = userAccountRepository.findByPhoneNumber(tokenIdentifier)
                            if (user != null) {
                                user.phoneVerified = true
                                userAccountRepository.save(user)
                            }
                        }
                    }

                    // 성공한 key 기준으로 guard 리셋
                    val successKey = guardKey(tokenChannel, tokenIdentifier)
                    val g = guardRepo.findById(successKey).orElseGet { VerificationGuardEntity(key = successKey) }
                    g.failedAttempts = 0
                    g.lockedUntil = null
                    guardRepo.save(g)
                }
            }

            return VerificationCodeConfirmResponse(
                verified = true,
                status = VerificationStatus.VERIFIED,
            )
        }

        // 실패 시도 카운트(요청 기준)
        val key = guardKey(channel, identifier)
        val g = guardRepo.findById(key).orElseGet { VerificationGuardEntity(key = key) }

        g.lockedUntil?.let { until ->
            if (until.isAfter(now())) {
                guardRepo.save(g)
                return VerificationCodeConfirmResponse(
                    verified = false,
                    status = VerificationStatus.LOCKED,
                    retryAfterSeconds = secondsUntil(until),
                    remainingAttempts = 0,
                )
            }
        }

        g.failedAttempts += 1
        val remaining = max(0, MAX_VERIFY_ATTEMPTS - g.failedAttempts)

        if (g.failedAttempts >= MAX_VERIFY_ATTEMPTS) {
            val until = now().plus(LOCK_DURATION)
            g.lockedUntil = until
            guardRepo.save(g)
            return VerificationCodeConfirmResponse(
                verified = false,
                status = VerificationStatus.LOCKED,
                retryAfterSeconds = secondsUntil(until),
                remainingAttempts = 0,
            )
        }

        guardRepo.save(g)
        return VerificationCodeConfirmResponse(
            verified = false,
            status = VerificationStatus.INVALID,
            remainingAttempts = remaining,
        )
    }
}
