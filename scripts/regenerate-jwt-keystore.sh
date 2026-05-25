#!/usr/bin/env bash
# regenerate-jwt-keystore.sh
# SEC-1 (티켓 2c270d30) 대응: 노출된 JWT 서명 키스토어를 동일 형식으로 재생성한다.
#
# 기존 키스토어 호환 사양(현 운영 application.yml 가정):
#   - 형식    : PKCS12 (.p12)
#   - 키      : RSA 2048bit
#   - 서명    : SHA256withRSA (RS256)
#   - alias   : jwt (JWT_KEY_ALIAS 기본값)
#   - subject : CN=triplan-auth, OU=dev, O=soo, L=Seoul, C=KR
#
# ⚠️ 이 스크립트는 "새 키스토어 파일 생성"만 한다.
#    운영/스테이징 cutover(교체+재기동)와 기존 토큰 무효화는 수행하지 않는다.
#    cutover 절차는 docs/security/jwt-keystore-rotation.md 참고. PM 승인 후 운영 반영.
set -euo pipefail

OUT="${OUT:-./jwt-new.p12}"
ALIAS="${JWT_KEY_ALIAS:-jwt}"
STOREPASS="${JWT_KEYSTORE_PASSWORD:?JWT_KEYSTORE_PASSWORD 환경변수 필요}"
KEYPASS="${JWT_KEY_PASSWORD:-$STOREPASS}"
DNAME="${DNAME:-CN=triplan-auth, OU=dev, O=soo, L=Seoul, C=KR}"
VALIDITY="${VALIDITY:-825}"   # 자체서명 인증서 유효일(서명검증엔 무관하나 관례상 설정)

if [ -e "$OUT" ]; then
  echo "거부: $OUT 가 이미 존재. 덮어쓰기 방지(기존 키 보존)를 위해 OUT 을 바꾸거나 수동 제거." >&2
  exit 1
fi

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -sigalg SHA256withRSA \
  -dname "$DNAME" \
  -validity "$VALIDITY" \
  -storetype PKCS12 \
  -keystore "$OUT" \
  -storepass "$STOREPASS" \
  -keypass "$KEYPASS"

echo "생성 완료: $OUT"
echo "--- 검증 ---"
keytool -list -v -keystore "$OUT" -storetype PKCS12 -storepass "$STOREPASS" \
  | grep -iE "별칭 이름|alias name|항목 유형|entry type|서명 알고리즘|signature algorithm|공용 키 알고리즘|public key algorithm|소유자|owner"
