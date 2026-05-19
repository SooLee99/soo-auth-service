package io.soo.springboot.clients.kakao

interface KakaoOAuthClient {
    /**
     * Kakao `/v2/user/me` 호출. Bearer 헤더로 사용자 accessToken 전달.
     *
     * @return Kakao API 응답을 원본 attrs(Map)로 변환한 결과. KakaoParser가 그대로 소비할 수 있는 형식.
     * @throws KakaoTokenInvalidException 401 응답 (만료/위조/권한 없음)
     * @throws KakaoProviderException 5xx 또는 네트워크/직렬화 실패
     */
    fun fetchUserMe(kakaoAccessToken: String): Map<String, Any?>
}

class KakaoTokenInvalidException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class KakaoProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
