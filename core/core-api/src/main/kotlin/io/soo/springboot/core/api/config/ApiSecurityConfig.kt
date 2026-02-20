package io.soo.springboot.core.api.config

import io.soo.springboot.core.api.security.LocalJsonLoginFilter
import io.soo.springboot.core.api.security.handler.OAuth2LoginSuccessHandler
import io.soo.springboot.core.api.security.handler.RestAccessDeniedHandler
import io.soo.springboot.core.api.security.handler.RestAuthenticationEntryPoint
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

@Configuration
class ApiSecurityConfig(
    private val securityContextRepository: SecurityContextRepository,
    private val restAuthenticationEntryPoint: RestAuthenticationEntryPoint,
    private val restAccessDeniedHandler: RestAccessDeniedHandler,
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
    fun h2ConsoleSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(H2_CONSOLE)

        http.csrf { it.disable() }

        // H2 console은 frame 사용 => sameOrigin 또는 disable 필요
        http.headers { headers ->
            headers.frameOptions { it.sameOrigin() } // 또는 headers.frameOptions { it.disable() }
        }

        http.authorizeHttpRequests { auth ->
            auth.anyRequest().permitAll()
        }

        // H2는 굳이 oauth2/jwt/filter 등 적용할 필요 없음
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

        http.authenticationProvider(daoAuthProvider)

        // ✅ 세션/시큐리티 컨텍스트(로컬 로그인 + OAuth2 state 저장에 필요)
        http.securityContext { it.securityContextRepository(securityContextRepository) }

        // ✅ 예외 처리(401/403) - API 스타일
        http.exceptionHandling { ex ->
            ex.authenticationEntryPoint(restAuthenticationEntryPoint)
            ex.accessDeniedHandler(restAccessDeniedHandler)
        }

        // ✅ CSRF 제외
        http.csrf { csrf -> csrf.ignoringRequestMatchers(*CSRF_IGNORED_ENDPOINTS) }

        // ✅ 불필요한 기본 로그인페이지/베이직 인증 끔
        http.formLogin { it.disable() }
        http.httpBasic { it.disable() }

        // ✅ OAuth2 Login 활성화
        http.oauth2Login { oauth ->
            oauth.authorizationEndpoint { ep -> ep.baseUri("/oauth2/authorization") } // 기본값
            oauth.redirectionEndpoint { ep -> ep.baseUri("/login/oauth2/code/*") }   // 기본값
            oauth.successHandler(oAuth2LoginSuccessHandler)
        }

        // ✅ JWT(Resource Server)
        http.oauth2ResourceServer { it.jwt { } }

        // ✅ OAuth2는 state 저장 때문에 세션 필요할 수 있음
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }

        http.authorizeHttpRequests { auth ->
            auth.requestMatchers(*PUBLIC_ENDPOINTS).permitAll()
            auth.requestMatchers(H2_CONSOLE).permitAll()
            auth.requestMatchers(ADMIN_API).permitAll()
            auth.requestMatchers(
                HttpMethod.GET,
                "/api/v1/auth/oauth2/*/authorize-url"
            ).permitAll()
            // ✅ 로그아웃은 인증 필요
            auth.requestMatchers("/api/v1/auth/**/logout").authenticated()

            // ✅ 나머지 API는 인증 필요
            auth.requestMatchers("/api/**").authenticated()
            auth.anyRequest().authenticated()
        }

        // ✅ 로컬 JSON 로그인 필터
        http.addFilterAt(localJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}