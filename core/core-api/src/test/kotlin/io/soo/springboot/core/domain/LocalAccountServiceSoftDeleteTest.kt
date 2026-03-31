package io.soo.springboot.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.local.phone.login.PhoneLoginResolver
import io.soo.springboot.core.domain.local.policy.UserUniquenessPolicy
import io.soo.springboot.core.domain.local.support.PhoneNumberNormalizer
import io.soo.springboot.core.domain.local.support.PhoneAccountFactory
import io.soo.springboot.core.domain.local.phone.PhoneVerificationService
import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLog
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant

class LocalAccountServiceSoftDeleteTest {
    private val userRepository = mockk<UserRepository>()
    private val localCredentialRepository = mockk<LocalCredentialRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val tokenRevocationService = mockk<TokenRevocationService>(relaxed = true)
    private val phoneVerificationService = mockk<PhoneVerificationService>(relaxed = true)
    private val auditRepository = mockk<UserStatusAuditLogRepository>()
    private val userUniquenessPolicy = mockk<UserUniquenessPolicy>(relaxed = true)
    private val phoneNumberNormalizer = PhoneNumberNormalizer()
    private val phoneAccountFactory = mockk<PhoneAccountFactory>(relaxed = true)
    private val phoneLoginResolver = mockk<PhoneLoginResolver>(relaxed = true)

    private val service = LocalAccountService(
        userRepository = userRepository,
        localAccountRepository = localCredentialRepository,
        passwordEncoder = passwordEncoder,
        tokenRevocationService = tokenRevocationService,
        phoneVerificationService = phoneVerificationService,
        userStatusAuditLogRepository = auditRepository,
        userUniquenessPolicy = userUniquenessPolicy,
        phoneNumberNormalizer = phoneNumberNormalizer,
        phoneAccountFactory = phoneAccountFactory,
        phoneLoginResolver = phoneLoginResolver,
    )

    @Test
    fun `탈퇴 처리 성공 및 보관 만료일 저장`() {
        every { userRepository.findByIdIncludingDeleted(1L) } returns activeUser()
        val saveSlot = slot<User>()
        every { userRepository.save(capture(saveSlot)) } answers { saveSlot.captured }
        every { localCredentialRepository.deleteByUserId(1L) } returns 1
        every {
            auditRepository.save(1L, 1L, AdminUserActionType.SOFT_DELETE, any())
        } returns UserStatusAuditLog(1L, 1L, 1L, AdminUserActionType.SOFT_DELETE, "privacy", Instant.now())

        service.softDelete(1L, "privacy")

        val saved = saveSlot.captured
        assertEquals(UserStatus.SOFT_DELETED, saved.userStatus)
        assertNotNull(saved.deletedAt)
        assertEquals("privacy", saved.deletionReason)
        assertNotNull(saved.retentionUntil)
        verify(exactly = 1) { localCredentialRepository.deleteByUserId(1L) }
        verify(exactly = 1) { tokenRevocationService.revokeAllByUserId(1L) }
    }

    @Test
    fun `탈퇴 시 개인정보 익명화 처리`() {
        every { userRepository.findByIdIncludingDeleted(1L) } returns activeUserWithPii()
        val saveSlot = slot<User>()
        every { userRepository.save(capture(saveSlot)) } answers { saveSlot.captured }
        every { localCredentialRepository.deleteByUserId(1L) } returns 1
        every {
            auditRepository.save(1L, 1L, AdminUserActionType.SOFT_DELETE, any())
        } returns UserStatusAuditLog(1L, 1L, 1L, AdminUserActionType.SOFT_DELETE, null, Instant.now())

        service.softDelete(1L, null)

        val saved = saveSlot.captured
        assertTrue(saved.email.startsWith("deleted+1."))
        assertTrue(saved.email.endsWith("@deleted.local"))
        assertEquals("deleted-1-${saved.deletedAt!!.epochSecond}", saved.phoneNumber)
        assertEquals(false, saved.emailVerified)
        assertEquals(false, saved.phoneVerified)
        assertEquals(io.soo.springboot.core.enums.Gender.UNKNOWN, saved.gender)
        assertNull(saved.name)
        assertNull(saved.nickname)
        assertNull(saved.birthyear)
        assertNull(saved.birthday)
        assertNull(saved.ageRange)
        assertNull(saved.locale)
        assertNull(saved.profileImageUrl)
        assertNull(saved.thumbnailImageUrl)
        assertNull(saved.oauthProviderUserId)
        assertNull(saved.oauthConnectedAt)
        assertNull(saved.oauthExtraJson)
        assertNull(saved.oauthRawJson)
    }

    private fun activeUser() = User(
        id = 1L,
        email = "user@example.com",
        phoneNumber = null,
        name = null,
        nickname = null,
        authProvider = AuthProvider.LOCAL,
        userStatus = UserStatus.ACTIVE,
    )

    private fun activeUserWithPii() = User(
        id = 1L,
        email = "pii@example.com",
        emailVerified = true,
        phoneNumber = "01012345678",
        phoneNumberE164 = "+821012345678",
        phoneVerified = true,
        name = "Hong Gildong",
        nickname = "hong",
        gender = io.soo.springboot.core.enums.Gender.MALE,
        locale = "ko-KR",
        birthyear = "1990",
        birthday = "01-01",
        ageRange = "30-39",
        profileImageUrl = "https://cdn.example.com/p.jpg",
        thumbnailImageUrl = "https://cdn.example.com/t.jpg",
        authProvider = AuthProvider.LOCAL,
        oauthProviderUserId = "oauth-123",
        oauthConnectedAt = Instant.now(),
        oauthExtraJson = "{\"k\":\"v\"}",
        oauthRawJson = "{\"raw\":true}",
        userStatus = UserStatus.ACTIVE,
    )
}
