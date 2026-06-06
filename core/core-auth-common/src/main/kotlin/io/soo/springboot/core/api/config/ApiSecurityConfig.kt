package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.access.RestAccessDeniedHandler
import io.soo.springboot.core.api.security.entrypoint.UnauthorizedEntryPoint
import io.soo.springboot.core.api.security.local.LocalJsonLoginFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class ApiSecurityConfig(
    private val securityContextRepository: SecurityContextRepository,
    private val unauthorizedEntryPoint: UnauthorizedEntryPoint,
    private val restAccessDeniedHandler: RestAccessDeniedHandler,
    private val oauth2SecurityConfigurers: List<OAuth2SecurityConfigurer>,
    @Qualifier("localJsonLoginFilter") private val localJsonLoginFilterProvider: ObjectProvider<LocalJsonLoginFilter>,
    @Qualifier("adminJsonLoginFilter") private val adminJsonLoginFilterProvider: ObjectProvider<LocalJsonLoginFilter>,
    // cors.origins (= env CORS_ORIGINS) 콤마 구분. 로컬 기본값 http://localhost:5173
    @Value("\${cors.origins:http://localhost:5173}") private val corsOrigins: String,
) {
    companion object {
        private val PUBLIC_ENDPOINTS = arrayOf(
            "/login",
            "/error",
            "/actuator/**",

            // local auth API
            "/api/v1/auth/local/login",
            "/api/v1/auth/admin/login",
            "/api/v1/auth/local/email/signup",
            "/api/v1/auth/local/phone/signup",
            "/api/v1/auth/local/phone/login",
            "/api/v1/auth/local/id/signup",
            "/api/v1/auth/local/id/login",
            "/api/v1/auth/local/phone-verifications/request",
            "/api/v1/auth/local/phone-verifications/confirm",
            "/api/v1/auth/local/token/refresh",
            "/api/v1/auth/local/session",

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
        private const val ADMIN_API = "/api/v1/auth/admin/**"
    }

    @Bean
    @Order(0)
    fun h2ConsoleChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(AntPathRequestMatcher(H2_CONSOLE))

        http.authorizeHttpRequests { it.anyRequest().permitAll() }
        http.csrf { it.disable() }

        http.headers { headers ->
            headers.frameOptions { it.sameOrigin() }
        }

        return http.build()
    }

    @Bean
    @Order(1)
    fun securityFilterChain(
        http: HttpSecurity,
        daoAuthProvider: DaoAuthenticationProvider,
    ): SecurityFilterChain {
        // ✅ /h2-console/** 는 이 체인에서 제외
        http.securityMatcher(NegatedRequestMatcher(AntPathRequestMatcher(H2_CONSOLE)))

        // ✅ CORS: 앱이 직접 처리(기존 Caddy oauth2-fwd CORS 프록시 대체). preflight(OPTIONS) 자동 허용.
        http.cors { it.configurationSource(corsConfigurationSource()) }

        http.authenticationProvider(daoAuthProvider)

        http.securityContext { it.securityContextRepository(securityContextRepository) }

        // ✅ API 요청에만 JSON 에러 핸들러 적용
        http.exceptionHandling { ex ->
            ex.defaultAuthenticationEntryPointFor(
                unauthorizedEntryPoint,
                AntPathRequestMatcher("/api/**"),
            )
            ex.defaultAccessDeniedHandlerFor(
                restAccessDeniedHandler,
                AntPathRequestMatcher("/api/**"),
            )
        }

        http.csrf { csrf ->
            csrf.ignoringRequestMatchers(*CSRF_IGNORED_ENDPOINTS)
        }

        // ✅ 기본 로그인 페이지 활성화 (GET /login)
        http.formLogin { form ->
            form.permitAll()
        }

        http.httpBasic { it.disable() }

        oauth2SecurityConfigurers.forEach { it.configure(http) }

        http.oauth2ResourceServer { resourceServer ->
            resourceServer.jwt { jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            }
        }

        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }

        http.authorizeHttpRequests { auth ->
            auth.requestMatchers("/favicon.ico").permitAll()
            auth.requestMatchers(*PUBLIC_ENDPOINTS).permitAll()
            auth.requestMatchers(ADMIN_API).hasRole("ADMIN")
            auth.requestMatchers("/docs/**").permitAll()
            auth.requestMatchers("/swagger-ui/**").permitAll()
            auth.requestMatchers("/swagger/**").permitAll()

            auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth2/*/authorize-url").permitAll()
            // triplan: Kakao SDK accessToken 검증 → 자체 JWT 교환 (인증 없이 진입)
            auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/oauth2/kakao/token").permitAll()
            auth.requestMatchers("/api/v1/auth/local/logout").authenticated()
            auth.requestMatchers("/api/**").authenticated()
            auth.anyRequest().authenticated()
        }
        adminJsonLoginFilterProvider.ifAvailable {
            http.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java)
        }
        localJsonLoginFilterProvider.ifAvailable {
            http.addFilterAt(it, UsernamePasswordAuthenticationFilter::class.java)
        }

        return http.build()
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Content-Type", "X-Device-Id", "Authorization")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            extractRoleAuthorities(jwt)
        }
        return converter
    }

    private fun extractRoleAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val rawRoles = jwt.getClaimAsStringList("roles").orEmpty()
        return rawRoles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { SimpleGrantedAuthority(it) }
    }
}
