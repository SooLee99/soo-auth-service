package io.soo.springboot.core.domain.token

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

import io.soo.springboot.core.api.config.AppJwtProperties


@Service
class AccessTokenService(
    private val jwtEncoder: JwtEncoder,
    private val props: AppJwtProperties,
) {
    fun issue(authentication: Authentication, userId: Long): Pair<String, Long> {
        val now = Instant.now()
        val expiresIn = props.accessTtlSeconds
        val exp = now.plusSeconds(expiresIn)

        val roles = authentication.authorities.map { it.authority }.sorted()
        val jti = UUID.randomUUID().toString()

        val claims = JwtClaimsSet.builder()
            .issuer(props.issuer)
            .subject(authentication.name)
            .issuedAt(now)
            .expiresAt(exp)
            .id(jti)
            .claim("roles", roles)
            .claim("uid", userId)
            .build()

        val headers = JwsHeader.with(SignatureAlgorithm.RS256)
            .keyId(props.keyId)
            .build()

        val token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).tokenValue
        return token to expiresIn
    }
}