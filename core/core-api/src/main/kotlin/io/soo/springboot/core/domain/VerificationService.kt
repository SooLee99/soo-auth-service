package io.soo.springboot.core.domain

import io.soo.springboot.core.api.controller.v1.response.VerificationCodeConfirmResponse
import io.soo.springboot.core.enums.VerificationChannel
import io.soo.springboot.core.enums.VerificationCodeResponse
import io.soo.springboot.core.enums.VerificationStatus
import io.soo.springboot.storage.db.core.*
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.random.Random

@Service
class VerificationService(
    private val guardRepo: VerificationGuardRepository,
    private val codeRepo: VerificationCodeRepository,
    private val userAccountRepository: UserAccountRepository,
    private val smsSender: SmsSender,
    private val emailSender: EmailSender,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private val COOLDOWN: Duration = Duration.ofSeconds(60)
        private val CODE_TTL: Duration = Duration.ofMinutes(5)

        private const val MAX_VERIFY_ATTEMPTS = 5
        private val LOCK_DURATION: Duration = Duration.ofMinutes(10)

        private val SEND_WINDOW: Duration = Duration.ofHours(1)
        private const val MAX_SEND_PER_WINDOW = 5

        private const val PURPOSE_PREFIX = "SIGNUP_VERIFY"
    }

    private fun now(): Instant = Instant.now()

    private fun normalizeIdentifier(raw: String): String = raw.trim().lowercase()

    private fun isValidIdentifier(channel: VerificationChannel, id: String): Boolean {
        if (id.isBlank()) return false
        return when (channel) {
            VerificationChannel.EMAIL -> id.contains("@") && id.length <= 200
            VerificationChannel.SMS -> id.length in 8..20
        }
    }

    private fun key(channel: VerificationChannel, identifier: String): String =
        "$PURPOSE_PREFIX:${channel.name}:${identifier}"

    private fun secondsUntil(t: Instant): Long =
        max(0, Duration.between(now(), t).seconds)

    private fun generate6Digits(): String {
        val n = secureRandom.nextInt(1_000_000) // 0..999999
        return n.toString().padStart(6, '0')
    }

    private fun messageFor(channel: VerificationChannel, code: String): Pair<String, String> {
        // (subject, body)
        return when (channel) {
            VerificationChannel.EMAIL ->
                "가입 인증번호" to "인증번호는 $code 입니다.\n유효시간은 ${CODE_TTL.toMinutes()}분입니다."
            VerificationChannel.SMS ->
                "" to "[가입] 인증번호 $code (유효 ${CODE_TTL.toMinutes()}분)"
        }
    }

    /**
     * ✅ 인증코드 발송/재발송 (항상 200 응답 패턴 유지)
     * - 6자리 코드 생성
     * - code_hash로 저장 (원문 저장 X)
     * - 쿨다운/시간창 제한/잠금 처리
     */
    @Transactional
    fun requestCode(channel: VerificationChannel, rawIdentifier: String): VerificationCodeResponse {
        val identifier = normalizeIdentifier(rawIdentifier)
        if (!isValidIdentifier(channel, identifier)) {
            return VerificationCodeResponse(VerificationStatus.INVALID_FORMAT)
        }

        val k = key(channel, identifier)
        val g = guardRepo.findById(k).orElseGet { VerificationGuardEntity(key = k) }

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

        // 2) 시간창 전송 제한(선택)
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

        // 4) 코드 생성 + 저장(해시)
        val code = generate6Digits()
        val expiresAt = n.plus(CODE_TTL)
        val hash = passwordEncoder.encode(code)

        val existing = codeRepo.findById(k).orElse(null)
        val entity = if (existing == null) {
            VerificationCodeEntity(
                key = k,
                codeHash = hash,
                expiresAt = expiresAt,
                lastIssuedAt = n,
            )
        } else {
            existing.codeHash = hash
            existing.expiresAt = expiresAt
            existing.lastIssuedAt = n
            existing
        }
        codeRepo.save(entity)

        // 5) 전송 (실패해도 외부로는 숨김: 계정열거/전송실패 노출 방지)
        try {
            val (subject, body) = messageFor(channel, code)
            when (channel) {
                VerificationChannel.EMAIL -> emailSender.send(identifier, subject, body)
                VerificationChannel.SMS -> smsSender.send(identifier, body)
            }
        } catch (e: Exception) {
            log.warn("verification send failed. channel=$channel identifier=$identifier", e)
        }

        // 6) guard 업데이트
        g.lastSentAt = n
        g.windowCount += 1
        guardRepo.save(g)

        return VerificationCodeResponse(
            status = VerificationStatus.SENT,
            retryAfterSeconds = COOLDOWN.seconds,
        )
    }

    /**
     * ✅ 인증코드 확인 (항상 200 응답 패턴)
     * - (channel+identifier)로 row를 찾고, hash 매칭
     * - 성공 시 code row 삭제(1회성)
     * - 실패 시 시도횟수 증가 + 잠금
     */
    @Transactional
    fun confirmCode(
        channel: VerificationChannel,
        rawIdentifier: String,
        rawCode: String,
    ): VerificationCodeConfirmResponse {
        val identifier = normalizeIdentifier(rawIdentifier)
        val code = rawCode.trim()

        if (!isValidIdentifier(channel, identifier) || code.length != 6 || !code.all { it.isDigit() }) {
            return VerificationCodeConfirmResponse(
                verified = false,
                status = VerificationStatus.INVALID,
            )
        }

        // 타이밍 힌트 완화
        Thread.sleep(Random.nextLong(10, 40))

        val k = key(channel, identifier)
        val g = guardRepo.findById(k).orElseGet { VerificationGuardEntity(key = k) }

        // 잠금 상태
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

        val codeRow = codeRepo.findById(k).orElse(null)
        val n = now()

        // 코드 없음/만료
        if (codeRow == null || codeRow.expiresAt.isBefore(n)) {
            if (codeRow != null) codeRepo.delete(codeRow)

            g.failedAttempts += 1
            val remaining = max(0, MAX_VERIFY_ATTEMPTS - g.failedAttempts)
            if (g.failedAttempts >= MAX_VERIFY_ATTEMPTS) {
                val until = n.plus(LOCK_DURATION)
                g.lockedUntil = until
                guardRepo.save(g)
                return VerificationCodeConfirmResponse(false, VerificationStatus.LOCKED, secondsUntil(until), 0)
            }
            guardRepo.save(g)
            return VerificationCodeConfirmResponse(false, VerificationStatus.INVALID, remainingAttempts = remaining)
        }

        // 해시 매칭
        val ok = passwordEncoder.matches(code, codeRow.codeHash)
        if (!ok) {
            g.failedAttempts += 1
            val remaining = max(0, MAX_VERIFY_ATTEMPTS - g.failedAttempts)
            if (g.failedAttempts >= MAX_VERIFY_ATTEMPTS) {
                val until = n.plus(LOCK_DURATION)
                g.lockedUntil = until
                guardRepo.save(g)
                return VerificationCodeConfirmResponse(false, VerificationStatus.LOCKED, secondsUntil(until), 0)
            }
            guardRepo.save(g)
            return VerificationCodeConfirmResponse(false, VerificationStatus.INVALID, remainingAttempts = remaining)
        }

        // ✅ 성공: 1회성 삭제 + guard 리셋
        codeRepo.delete(codeRow)
        g.failedAttempts = 0
        g.lockedUntil = null
        guardRepo.save(g)

        // ✅ verified 처리
        when (channel) {
            VerificationChannel.EMAIL -> {
                val user = userAccountRepository.findByEmail(identifier)
                if (user != null) {
                    user.emailVerified = true
                    userAccountRepository.save(user)
                }
            }
            VerificationChannel.SMS -> {
                val user = userAccountRepository.findByPhoneNumber(identifier)
                if (user != null) {
                    user.phoneVerified = true
                    userAccountRepository.save(user)
                }
            }
        }

        return VerificationCodeConfirmResponse(
            verified = true,
            status = VerificationStatus.VERIFIED,
        )
    }
}
