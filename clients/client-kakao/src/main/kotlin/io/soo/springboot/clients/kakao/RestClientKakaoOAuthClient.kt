package io.soo.springboot.clients.kakao

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

@Configuration
class KakaoOAuthClientConfig {
    @Bean
    fun kakaoOAuthRestClient(
        @Value("\${triplan.kakao.user-me-base-url:https://kapi.kakao.com}") baseUrl: String,
    ): RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()
}

@Component
class RestClientKakaoOAuthClient(
    private val kakaoOAuthRestClient: RestClient,
) : KakaoOAuthClient {

    @Suppress("UNCHECKED_CAST")
    override fun fetchUserMe(kakaoAccessToken: String): Map<String, Any?> {
        return try {
            kakaoOAuthRestClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $kakaoAccessToken")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    throw KakaoTokenInvalidException("kakao /v2/user/me 4xx: ${response.statusCode}")
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    throw KakaoProviderException("kakao /v2/user/me 5xx: ${response.statusCode}")
                }
                .body(Map::class.java) as Map<String, Any?>?
                ?: throw KakaoProviderException("kakao /v2/user/me empty body")
        } catch (e: KakaoTokenInvalidException) {
            throw e
        } catch (e: KakaoProviderException) {
            throw e
        } catch (e: HttpClientErrorException) {
            throw KakaoTokenInvalidException("kakao /v2/user/me 4xx", e)
        } catch (e: HttpServerErrorException) {
            throw KakaoProviderException("kakao /v2/user/me 5xx", e)
        } catch (e: ResourceAccessException) {
            throw KakaoProviderException("kakao /v2/user/me network error", e)
        }
    }
}
