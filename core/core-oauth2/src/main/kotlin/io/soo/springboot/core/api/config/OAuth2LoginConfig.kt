package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.oauth2.OAuth2LoginSuccessHandler
import io.soo.springboot.core.domain.auth.AuthFeature
import io.soo.springboot.core.domain.auth.AuthFeatureGuard
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.stereotype.Component

@Component
class OAuth2LoginConfig(
    private val authFeatureGuard: AuthFeatureGuard,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
) : OAuth2SecurityConfigurer {
    override fun configure(http: HttpSecurity) {
        if (!authFeatureGuard.isEnabled(AuthFeature.OAUTH2)) {
            return
        }
        http.oauth2Login { oauth ->
            oauth.authorizationEndpoint { ep -> ep.baseUri("/oauth2/authorization") }
            oauth.redirectionEndpoint { ep -> ep.baseUri("/login/oauth2/code/*") }
            oauth.successHandler(oAuth2LoginSuccessHandler)
        }
    }
}
