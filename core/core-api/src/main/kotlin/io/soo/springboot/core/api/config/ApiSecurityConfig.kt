package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.access.RestAccessDeniedHandler
import io.soo.springboot.core.api.security.entrypoint.UnauthorizedEntryPoint
import io.soo.springboot.core.api.security.local.LocalJsonLoginFilter
import org.springframework.beans.factory.annotation.Qualifier
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

            // local auth API
            "/api/v1/auth/local/login",
            "/api/v1/auth/admin/login",
            "/api/v1/auth/local/signup",
            "/api/v1/auth/local/signup/phone",
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
        @Qualifier("localJsonLoginFilter") localJsonLoginFilter: LocalJsonLoginFilter,
        @Qualifier("adminJsonLoginFilter") adminJsonLoginFilter: LocalJsonLoginFilter,
        daoAuthProvider: DaoAuthenticationProvider,
    ): SecurityFilterChain {
        // ✅ /h2-console/** 는 이 체인에서 제외
        http.securityMatcher(NegatedRequestMatcher(AntPathRequestMatcher(H2_CONSOLE)))

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

        oAuth2LoginConfig.configure(http)

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
            auth.requestMatchers("/api/v1/auth/local/logout").authenticated()
            auth.requestMatchers("/api/**").authenticated()
            auth.anyRequest().authenticated()
        }
        http.addFilterBefore(adminJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)
        http.addFilterAt(localJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
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
