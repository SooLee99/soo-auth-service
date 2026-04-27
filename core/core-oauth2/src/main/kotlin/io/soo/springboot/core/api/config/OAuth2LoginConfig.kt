package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.oauth2.OAuth2LoginSuccessHandler
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.stereotype.Component

@Component
class OAuth2LoginConfig(
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
) : OAuth2SecurityConfigurer {
    override fun configure(http: HttpSecurity) {
        http.oauth2Login { oauth ->
            oauth.authorizationEndpoint { ep -> ep.baseUri("/oauth2/authorization") }
            oauth.redirectionEndpoint { ep -> ep.baseUri("/login/oauth2/code/*") }
            oauth.successHandler(oAuth2LoginSuccessHandler)
        }
    }
}
