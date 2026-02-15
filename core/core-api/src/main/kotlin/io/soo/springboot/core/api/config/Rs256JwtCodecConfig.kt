package io.soo.springboot.core.api.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPair
import java.security.KeyStore
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Configuration
@EnableConfigurationProperties(AppJwtProperties::class)
class Rs256JwtCodecConfig(
    private val props: AppJwtProperties,
    private val resourceLoader: ResourceLoader,
) {

    @Bean
    fun rsaKeyPair(): KeyPair = loadKeyPair()

    @Bean
    fun rsaPublicKey(keyPair: KeyPair): RSAPublicKey = keyPair.public as RSAPublicKey

    @Bean
    fun rsaPrivateKey(keyPair: KeyPair): RSAPrivateKey = keyPair.private as RSAPrivateKey

    @Bean
    fun jwtEncoder(rsaPublicKey: RSAPublicKey, rsaPrivateKey: RSAPrivateKey): JwtEncoder {
        val jwk = RSAKey.Builder(rsaPublicKey)
            .privateKey(rsaPrivateKey)
            .keyID(props.keyId)
            .build()

        val jwkSet = JWKSet(jwk)
        val jwkSource = ImmutableJWKSet<SecurityContext>(jwkSet)
        return NimbusJwtEncoder(jwkSource)
    }

    private fun loadKeyPair(): KeyPair {
        val ksProps = props.keystore
        val location = ksProps.location
        val password = ksProps.password.toCharArray()
        val alias = ksProps.alias
        val keyPassword = (ksProps.keyPassword ?: ksProps.password).toCharArray()

        val resource = resourceLoader.getResource(location)
        check(resource.exists()) {
            "JWT keystore not found: $location (e.g. core-api/src/main/resources/keys/jwt.p12 or use file:...)"
        }

        val ks = KeyStore.getInstance("PKCS12")
        try {
            resource.inputStream.use { ks.load(it, password) }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to load PKCS12 keystore. location=$location, alias=$alias. " +
                        "Check file integrity (not empty/corrupted) and passwords.",
                e
            )
        }

        val privateKey = ks.getKey(alias, keyPassword) as? RSAPrivateKey
            ?: throw IllegalStateException("Private key not found. alias=$alias (Entry must be PrivateKeyEntry)")

        val cert = ks.getCertificate(alias)
            ?: throw IllegalStateException("Certificate not found. alias=$alias")

        val publicKey = cert.publicKey as? RSAPublicKey
            ?: throw IllegalStateException("Public key is not RSA. alias=$alias")

        return KeyPair(publicKey, privateKey)
    }
}
