package io.soo.springboot.core.enums

enum class VerificationStatus {
    SENT,          // 발송 요청 (발송 실패에도 동일한 응답 패턴 유지)
    COOLDOWN,      // 쿨다운 중
    LOCKED,        // 시도 제한으로 잠김
    VERIFIED,      // (확인) 성공
    INVALID,       // (확인) 코드 불일치/만료/없는 사용자 등
    INVALID_FORMAT // identifier 형식 오류
}
