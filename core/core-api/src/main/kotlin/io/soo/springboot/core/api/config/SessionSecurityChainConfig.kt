package io.soo.springboot.core.api.config

import io.soo.springboot.core.domain.DBSessionRevokeLogoutHandler
import io.soo.springboot.core.domain.DevicePolicyFilter
import io.soo.springboot.core.domain.OAuth2LoginFailureHandler
import io.soo.springboot.core.domain.OAuth2LoginSuccessHandler
import io.soo.springboot.core.domain.LocalJsonLoginFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.context.SecurityContextRepository

@Configuration
class SessionSecurityChainConfig(
    private val securityContextRepository: SecurityContextRepository,

    // OAuth2 로그인 핸들러
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val oauth2LoginFailureHandler: OAuth2LoginFailureHandler,

    // 로그인 이후 정책 필터
    private val devicePolicyFilter: DevicePolicyFilter,

    // 로그아웃 시 DB revoke 처리
    private val dbLogoutHandler: DBSessionRevokeLogoutHandler,
) {

    /**
     * ✅ (2) 세션/OAuth2/로컬로그인 체인
     * - /api/v1/auth/login 은 LocalJsonLoginFilter가 처리
     */
    @Bean
    @Order(2)
    fun sessionChain(
        http: HttpSecurity,
        localJsonLoginFilter: LocalJsonLoginFilter,
    ): SecurityFilterChain {

        http
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .csrf { csrf ->
                csrf.ignoringRequestMatchers(
                    "/api/v1/auth/**",
                    "/oauth2/**",
                    "/login/**",
                    "/h2-console/**",
                )
            }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() } // H2 console
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/oauth2/**",
                    "/login/**",
                    "/error",
                    "/actuator/**",
                ).permitAll()

                it.requestMatchers("/api/v1/admin/**").permitAll() // 필요시 hasRole("ADMIN")
                it.requestMatchers("/h2-console/**").permitAll()

                it.anyRequest().authenticated()
            }
            .formLogin { it.disable() }
            .oauth2Login { oauth ->
                oauth.successHandler(oauth2LoginSuccessHandler)
                oauth.failureHandler(oauth2LoginFailureHandler)
            }
            // 로컬(JSON) 로그인 필터 & 로그인 이후 정책(차단/세션 revoke)
            .addFilterAt(localJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(devicePolicyFilter, AuthorizationFilter::class.java)

            // 로그아웃
            .logout {
                it.logoutUrl("/api/v1/auth/session/logout")
                it.addLogoutHandler(dbLogoutHandler)
                it.logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler())
            }

        return http.build()
    }
}
