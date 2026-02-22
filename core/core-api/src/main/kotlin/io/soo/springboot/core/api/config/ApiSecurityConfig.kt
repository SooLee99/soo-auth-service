package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.local.LocalJsonLoginFilter
import io.soo.springboot.core.api.security.oauth2.OAuth2LoginSuccessHandler
import io.soo.springboot.core.api.security.access.RestAccessDeniedHandler
import io.soo.springboot.core.api.security.entrypoint.UnauthorizedEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
@Configuration
class ApiSecurityConfig(
    private val securityContextRepository: SecurityContextRepository,
    private val unauthorizedEntryPoint: UnauthorizedEntryPoint,
    private val restAccessDeniedHandler: RestAccessDeniedHandler,
    private val oAuth2LoginConfig: OAuth2LoginConfig,
) {
    companion object {
        private val PUBLIC_ENDPOINTS = arrayOf(
            "/login",
            "/error",
            "/actuator/**",

            // ✅ local auth API
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/local/signup",
            "/api/v1/auth/local/token/refresh",

            // ✅ OAuth2 시작/콜백
            "/oauth2/authorization/**",
            "/login/oauth2/**",
        )

        private val CSRF_IGNORED_ENDPOINTS = arrayOf(
            "/api/**",
            "/oauth2/**",
            "/login/oauth2/**",
            "/h2-console/**",
        )

        private const val H2_CONSOLE = "/h2-console/**"
        private const val ADMIN_API = "/api/v1/admin/**"
    }

    @Bean
    @Order(0)
    fun h2ConsoleChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(AntPathRequestMatcher("/h2-console/**"))

        http.authorizeHttpRequests { it.anyRequest().permitAll() }
        http.csrf { it.disable() }

        // ✅ H2 콘솔은 프레임 기반 -> SAMEORIGIN 허용
        http.headers { headers ->
            headers.frameOptions { it.sameOrigin() }
        }

        return http.build()
    }

    @Bean
    @Order(1)
    fun securityFilterChain(
        http: HttpSecurity,
        localJsonLoginFilter: LocalJsonLoginFilter,
        daoAuthProvider: DaoAuthenticationProvider,
        oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    ): SecurityFilterChain {

        // ✅ 이 체인은 /h2-console/** 요청을 절대 처리하지 않게 "제외"
        http.securityMatcher(NegatedRequestMatcher(AntPathRequestMatcher("/h2-console/**")))

        http.authenticationProvider(daoAuthProvider)

        http.securityContext { it.securityContextRepository(securityContextRepository) }

        http.exceptionHandling { ex ->
            ex.authenticationEntryPoint(unauthorizedEntryPoint)
            ex.accessDeniedHandler(restAccessDeniedHandler)
        }

        http.csrf { csrf -> csrf.ignoringRequestMatchers(*CSRF_IGNORED_ENDPOINTS) }

        http.formLogin { it.disable() }
        http.httpBasic { it.disable() }

        oAuth2LoginConfig.configure(http)

        http.oauth2ResourceServer { it.jwt { } }

        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }

        http.authorizeHttpRequests { auth ->
            auth.requestMatchers(*PUBLIC_ENDPOINTS).permitAll()

            // ❌ 여기서 H2 permitAll 제거 (H2는 0번 체인에서만 처리)
            // auth.requestMatchers(H2_CONSOLE).permitAll()

            auth.requestMatchers(ADMIN_API).permitAll()
            auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth2/*/authorize-url").permitAll()
            auth.requestMatchers("/api/v1/auth/**/logout").authenticated()

            auth.requestMatchers("/api/**").authenticated()
            auth.anyRequest().authenticated()
        }

        http.addFilterAt(localJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
