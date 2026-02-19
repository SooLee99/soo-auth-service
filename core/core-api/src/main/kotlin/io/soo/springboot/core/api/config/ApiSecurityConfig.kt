package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.handler.RestAccessDeniedHandler
import io.soo.springboot.core.api.security.handler.RestAuthenticationEntryPoint
import io.soo.springboot.core.api.security.LocalJsonLoginFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.SecurityContextRepository

@Configuration
class ApiSecurityConfig(
    private val securityContextRepository: SecurityContextRepository,
    private val restAuthenticationEntryPoint: RestAuthenticationEntryPoint,
    private val restAccessDeniedHandler: RestAccessDeniedHandler,
) {
    companion object {
        private val PUBLIC_ENDPOINTS = arrayOf(
            "/login/**",
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/error",
            "/actuator/**",
        )

        private val CSRF_IGNORED_ENDPOINTS = arrayOf(
            "/login/**",
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/h2-console/**",
            "/api/**", // ✅ API는 보통 Bearer(JWT)이므로 CSRF 제외
        )

        private const val H2_CONSOLE = "/h2-console/**"
        private const val ADMIN_API = "/api/v1/admin/**"
    }

    @Bean
    @Order(0)
    fun securityFilterChain(
        http: HttpSecurity,
        localJsonLoginFilter: LocalJsonLoginFilter,
        daoAuthProvider: DaoAuthenticationProvider,
    ): SecurityFilterChain {

        http.authenticationProvider(daoAuthProvider)

        // ✅ 세션/시큐리티컨텍스트(로컬 로그인용)도 사용
        http.securityContext {
            it.securityContextRepository(securityContextRepository)
        }

        // ✅ 예외 처리(401/403)
        http.exceptionHandling { ex ->
            ex.authenticationEntryPoint(restAuthenticationEntryPoint)
            ex.accessDeniedHandler(restAccessDeniedHandler)
        }

        // ✅ CSRF: 필요한 것만 제외
        http.csrf { csrf ->
            csrf.ignoringRequestMatchers(*CSRF_IGNORED_ENDPOINTS)
        }

        // ✅ JWT(Resource Server)도 같이 켬 (Bearer 토큰이 있을 때만 동작)
        http.oauth2ResourceServer { it.jwt { } }

        // ✅ 세션이 “필요할 때만” 생성되도록 (JWT는 원래 stateless)
        http.sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        }

        // ✅ 인가 규칙: 순서 중요(구체적인 것 → 일반적인 것)
        http.authorizeHttpRequests { auth ->
            // 1) 공개
            auth.requestMatchers(*PUBLIC_ENDPOINTS).permitAll()
            auth.requestMatchers(H2_CONSOLE).permitAll()

            // 2) auth 하위에서 logout만 인증 필요
            auth.requestMatchers("/api/v1/auth/logout").authenticated()
            auth.requestMatchers("/api/v1/auth/**").permitAll()

            // 3) admin (개발용 permitAll 유지 / 필요시 hasRole로 변경)
            auth.requestMatchers(ADMIN_API).permitAll()

            // 4) 나머지 API는 인증 필요
            auth.requestMatchers("/api/**").authenticated()

            // 5) 그 외도 인증 필요(원하면 permitAll로 조정)
            auth.anyRequest().authenticated()
        }

        // ✅ 로컬 JSON 로그인 필터
        http.addFilterAt(localJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
