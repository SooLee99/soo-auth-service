package io.soo.springboot.core.api.config

import io.soo.springboot.core.domain.JwtDenylistService
import io.soo.springboot.core.domain.RevocationAwareJwtDecoder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Profile("!local")
@Configuration
class JwtDecoderConfig {

    /**
     * ✅ (원본) JWT 디코더
     * - Spring Boot Resource Server 설정값으로부터 생성
     * - jwk-set-uri 우선, 없으면 issuer-uri 사용
     */
    @Bean(name = ["baseJwtDecoder"])
    fun baseJwtDecoder(props: OAuth2ResourceServerProperties): JwtDecoder {
        val jwt = props.jwt

        val jwkSetUri = jwt.jwkSetUri
        if (!jwkSetUri.isNullOrBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
        }

        val issuerUri = jwt.issuerUri
        if (!issuerUri.isNullOrBlank()) {
            return JwtDecoders.fromIssuerLocation(issuerUri)
        }

        throw IllegalStateException(
            "JWT Decoder 설정이 없습니다. " +
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri 또는 issuer-uri를 설정하세요."
        )
    }

    /**
     * ✅ (최종) Denylist(블랙리스트) 체크가 포함된 JWT 디코더
     * - Resource Server는 이 JwtDecoder를 사용하게 됨 (@Primary)
     */
    @Bean
    @Primary
    fun jwtDecoder(
        @Qualifier("baseJwtDecoder") delegate: JwtDecoder,
        denylist: JwtDenylistService,
    ): JwtDecoder = RevocationAwareJwtDecoder(delegate, denylist)
}
