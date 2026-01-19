package io.soo.springboot.core.domain

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.OAuthIdentityEntity
import io.soo.springboot.storage.db.core.OAuthIdentityRepository
import io.soo.springboot.storage.db.core.UserAccountEntity
import io.soo.springboot.storage.db.core.UserAccountRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UserAccountService(
    private val userAccountRepository: UserAccountRepository,
    private val oauthIdentityRepository: OAuthIdentityRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MAX_PROVIDER_USER_ID = 255
    }

    @Transactional
    fun upsertFromOAuth2(token: OAuth2AuthenticationToken, request: HttpServletRequest): Pair<Long, String> {
        val info = extractUserInfo(token)
        val provider = info.provider
        val providerUserId = info.providerUserId.trim().take(MAX_PROVIDER_USER_ID)

        // ✅ raw attributes / (선택) id_token claims 저장
        val rawAttributesJson = safeJson(token.principal.attributes)
        val idTokenClaimsJson = (token.principal as? OidcUser)?.let { safeJson(it.claims) }

        // 1) oauth_identity 먼저 찾기(락)
        val existingIdentity = oauthIdentityRepository.lockByProviderAndProviderUserId(provider, providerUserId)

        val user: UserAccountEntity = if (existingIdentity != null) {
            userAccountRepository.findById(existingIdentity.userId).orElseThrow {
                IllegalStateException("oauth_identity.userId=${existingIdentity.userId} not found")
            }
        } else {
            // 2) 없으면 이메일로 유저 찾거나 생성
            val byEmail = info.email?.let { userAccountRepository.findByEmail(it) }
            val createdOrExisting = byEmail ?: userAccountRepository.save(
                UserAccountEntity(
                    email = info.email,
                    emailVerified = info.emailVerified ?: false,
                    nickname = info.nickname,
                    name = info.name,
                    givenName = info.givenName,
                    familyName = info.familyName,
                    locale = info.locale,

                    gender = info.gender,
                    ageRange = info.ageRange,
                    birthday = info.birthday,
                    birthyear = info.birthyear,
                    phoneNumber = info.phoneNumber,

                    profileImageUrl = info.profileImageUrl,
                    thumbnailImageUrl = info.thumbnailImageUrl,

                    lastLoginProvider = provider,
                    lastLoginAt = Instant.now(),
                ),
            )

            // 3) oauth_identity 연결(동시성 uq 충돌 가능)
            val identity = try {
                oauthIdentityRepository.save(
                    OAuthIdentityEntity(
                        userId = createdOrExisting.id,
                        provider = provider,
                        providerUserId = providerUserId,
                        ci = info.ci,
                        rawAttributesJson = rawAttributesJson,
                        idTokenClaimsJson = idTokenClaimsJson,
                    ),
                )
            } catch (_: DataIntegrityViolationException) {
                oauthIdentityRepository.lockByProviderAndProviderUserId(provider, providerUserId)
                    ?: throw IllegalStateException("oauth_identity upsert retry failed: $provider/$providerUserId")
            }

            if (identity.userId != createdOrExisting.id) {
                userAccountRepository.findById(identity.userId).orElseThrow {
                    IllegalStateException("oauth_identity.userId=${identity.userId} not found")
                }
            } else {
                createdOrExisting
            }
        }

        // ✅ 4) 매 로그인마다 “최신 정보” 반영 + lastLogin 업데이트
        mergeLatest(user, info)
        user.lastLoginProvider = provider
        user.lastLoginAt = Instant.now()
        userAccountRepository.save(user)

        // ✅ 5) identity에도 raw 최신화(원하면)
        existingIdentity?.let { id ->
            id.ci = id.ci ?: info.ci
            id.rawAttributesJson = rawAttributesJson
            if (id.idTokenClaimsJson == null && idTokenClaimsJson != null) id.idTokenClaimsJson = idTokenClaimsJson
            oauthIdentityRepository.save(id)
        }

        return user.id to providerUserId
    }

    private fun mergeLatest(user: UserAccountEntity, info: OAuthUserInfo) {
        // null이면 기존 값 유지(덮어쓰지 않음)
        user.email = user.email ?: info.email
        if (info.emailVerified == true) user.emailVerified = true

        user.nickname = info.nickname ?: user.nickname
        user.name = info.name ?: user.name
        user.givenName = info.givenName ?: user.givenName
        user.familyName = info.familyName ?: user.familyName
        user.locale = info.locale ?: user.locale

        user.gender = info.gender ?: user.gender
        user.ageRange = info.ageRange ?: user.ageRange
        user.birthday = info.birthday ?: user.birthday
        user.birthyear = info.birthyear ?: user.birthyear
        user.phoneNumber = info.phoneNumber ?: user.phoneNumber

        user.profileImageUrl = info.profileImageUrl ?: user.profileImageUrl
        user.thumbnailImageUrl = info.thumbnailImageUrl ?: user.thumbnailImageUrl
    }

    fun extractUserInfo(token: OAuth2AuthenticationToken): OAuthUserInfo {
        val regId = token.authorizedClientRegistrationId.lowercase()
        val attrs = token.principal.attributes

        return when (regId) {
            "kakao" -> parseKakao(attrs)
            "naver" -> parseNaver(attrs)
            "google" -> parseGoogle(attrs)
            else -> error("Unsupported provider: $regId")
        }
    }

    private fun parseKakao(attrs: Map<String, Any?>): OAuthUserInfo {
        val id = (attrs["id"] ?: error("kakao id missing")).toString()
        val kakaoAccount = attrs["kakao_account"] as? Map<*, *>
        val profile = kakaoAccount?.get("profile") as? Map<*, *>

        // Kakao는 동의 항목에 따라 내려오는 키가 달라집니다. (gender/age_range/birthday/birthyear/phone_number/ci 등) :contentReference[oaicite:2]{index=2}
        return OAuthUserInfo(
            provider = AuthProvider.KAKAO,
            providerUserId = id,

            email = kakaoAccount?.get("email") as? String,
            emailVerified = (kakaoAccount?.get("is_email_verified") as? Boolean),

            nickname = profile?.get("nickname") as? String,
            name = kakaoAccount?.get("name") as? String, // 있을 수도/없을 수도

            gender = kakaoAccount?.get("gender") as? String,
            ageRange = kakaoAccount?.get("age_range") as? String,
            birthday = kakaoAccount?.get("birthday") as? String,
            birthyear = kakaoAccount?.get("birthyear") as? String,
            phoneNumber = kakaoAccount?.get("phone_number") as? String,
            ci = kakaoAccount?.get("ci") as? String,

            thumbnailImageUrl = profile?.get("thumbnail_image_url") as? String,
            profileImageUrl = profile?.get("profile_image_url") as? String,
        )
    }

    private fun parseNaver(attrs: Map<String, Any?>): OAuthUserInfo {
        val resp = attrs["response"] as? Map<*, *> ?: error("naver response missing")
        val id = (resp["id"] ?: error("naver id missing")).toString()

        // Naver 프로필 응답에는 name/nickname/email/gender/age/birthday/birthyear/mobile/profile_image 등이 포함될 수 있습니다. :contentReference[oaicite:3]{index=3}
        return OAuthUserInfo(
            provider = AuthProvider.NAVER,
            providerUserId = id,

            email = resp["email"] as? String,
            nickname = (resp["nickname"] as? String),
            name = (resp["name"] as? String),

            gender = resp["gender"] as? String,
            ageRange = resp["age"] as? String,
            birthday = resp["birthday"] as? String,
            birthyear = resp["birthyear"] as? String,
            phoneNumber = resp["mobile"] as? String,

            profileImageUrl = resp["profile_image"] as? String,
            thumbnailImageUrl = resp["profile_image"] as? String,
        )
    }

    private fun parseGoogle(attrs: Map<String, Any?>): OAuthUserInfo {
        val id = (attrs["sub"] ?: error("google sub missing")).toString()
        return OAuthUserInfo(
            provider = AuthProvider.GOOGLE,
            providerUserId = id,

            email = attrs["email"] as? String,
            emailVerified = attrs["email_verified"] as? Boolean,

            name = attrs["name"] as? String,
            givenName = attrs["given_name"] as? String,
            familyName = attrs["family_name"] as? String,
            locale = attrs["locale"] as? String,

            nickname = (attrs["name"] as? String) ?: (attrs["given_name"] as? String),

            profileImageUrl = attrs["picture"] as? String,
            thumbnailImageUrl = attrs["picture"] as? String,
        )
    }

    private fun safeJson(any: Any?): String =
        runCatching { objectMapper.writeValueAsString(any) }
            .getOrElse { "{\"error\":\"json-serialize-failed\",\"message\":\"${it.message}\"}" }
}
