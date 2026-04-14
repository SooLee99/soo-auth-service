package io.soo.springboot.core.domain.auth

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.stereotype.Service

@Service
class AuthFeatureGuard(
    private val authFeatureProperties: AuthFeatureProperties,
) {
    fun assertEnabled(feature: AuthFeature) {
        if (!isEnabled(feature)) {
            throw CoreException(
                ErrorType.AUTH_METHOD_DISABLED,
                data = mapOf("feature" to feature.name),
            )
        }
    }

    fun assertAnyEnabled(vararg features: AuthFeature) {
        if (features.none(::isEnabled)) {
            throw CoreException(
                ErrorType.AUTH_METHOD_DISABLED,
                data = mapOf("feature" to features.joinToString(",") { it.name }),
            )
        }
    }

    fun isEnabled(feature: AuthFeature): Boolean {
        return when (feature) {
            AuthFeature.EMAIL -> authFeatureProperties.email
            AuthFeature.PHONE -> authFeatureProperties.phone
            AuthFeature.ID -> authFeatureProperties.id
            AuthFeature.OAUTH2 -> authFeatureProperties.oauth2
        }
    }
}
