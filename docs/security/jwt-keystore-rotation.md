# JWT 키스토어 회전·무효화 절차 (SEC-1 / 티켓 2c270d30)

> 상태: **절차 문서 (운영 cutover 미실행)**. 운영/스테이징 실제 교체와 토큰 무효화는
> 사용자 영향이 크므로 PM 승인 후 진행한다. 히스토리 purge(force-push)는 보안 검토(9d8d4e9a)
> 승인 필수 — 단독 force-push 금지.

## 1. 배경 (트리아지 확정)

- repo가 PUBLIC이고 `origin/develop` 히스토리에 서명 키스토어 `jwt.p12`(2594B)·`jwt-keystore.p12`(2562B)가 실재.
- 현재 HEAD는 클린이나 약 20일간 공개 노출 → **서명용 개인키 손상(compromised)으로 간주**.
- 결과: 노출 키로 **누구나 유효한 JWT를 위조**할 수 있음. 키 교체 + 노출 키 신뢰 중단이 필수.

## 2. 키스토어 호환 사양

| 항목 | 값 |
|---|---|
| 형식 | PKCS12 (`.p12`) |
| 키 | RSA 2048bit |
| 서명 | SHA256withRSA (RS256) |
| alias | `jwt` (`JWT_KEY_ALIAS` 기본값) |
| subject | `CN=triplan-auth, OU=dev, O=soo, L=Seoul, C=KR` |
| 설정 위치 | `core/core-api/src/main/resources/application.yml` → `security.jwt.keystore.*` |
| key-id(kid) | 현재 `triplan-dev-1` (회전 시 새 kid 부여 권장) |
| TTL | access 900s(15m) / refresh 1209600s(14d) |

## 3. 새 키스토어 재생성 (a)

`scripts/regenerate-jwt-keystore.sh` 사용 — 위 사양과 동일한 키스토어를 생성하고 즉시 검증한다.

```bash
# 환경/스테이징/운영별로 비밀번호를 분리해 생성 (dev 값 재사용 금지)
OUT=./jwt-<env>.p12 \
JWT_KEYSTORE_PASSWORD='<새 store 비밀번호>' \
JWT_KEY_PASSWORD='<새 key 비밀번호>' \
bash scripts/regenerate-jwt-keystore.sh
```

- 로컬 dev 키 `keys/jwt-dev.p12`는 노출 파일(`jwt.p12`/`jwt-keystore.p12`)과 **다른 파일**이며 2026-05-25 생성된 클린 키 → dev는 추가 조치 불필요.
- 운영/스테이징 키는 노출 키 계열일 수 있으므로 **반드시 새로 생성**한다.

## 4. keys/ gitignore 재발 방지 (b) — 완료

`.gitignore`에 다음 적용 (기존엔 `jwt-dev.p12` 정확경로만 무시되어 `jwt.p12`/`jwt-keystore.p12`가 누락됐음):

```
*.p12
*.jks
core/core-api/src/main/resources/keys/
```

검증: `git check-ignore -v core/core-api/src/main/resources/keys/jwt.p12` → 매칭 확인.

## 5. 운영/스테이징 cutover 절차 (c) — ⚠️ PM 승인 후 실행

1. 새 키스토어를 환경별 시크릿 저장소(예: 배포 `.env` / 시크릿 매니저)에 배치. 저장소에 **커밋 금지**.
2. `JWT_KEYSTORE_PASSWORD` / `JWT_KEY_PASSWORD`를 새 값으로 교체.
3. (권장) `key-id`를 새 값(예: `triplan-<env>-2`)으로 변경 — JWKS 소비자가 구/신 키를 구분.
4. 무중단이면 롤링 재기동, 아니면 점검창에서 재기동.
5. 기동 후 `/api/v1/auth/oauth2/kakao/authorize-url` 등으로 정상 서명/검증 확인.

**영향(반드시 사전 공지):** 노출 키로 서명된 **모든 access·refresh 토큰이 즉시 무효화** → 전 사용자 강제 재로그인.

## 6. 노출 키 서명 토큰 무효화 전략 (d)

키가 **손상**됐으므로, 일반적인 무중단 dual-key(구 키도 한동안 검증 허용) 전략은 **사용 불가** —
구 키를 계속 신뢰하면 공격자가 위조한 토큰도 통과하기 때문이다. 따라서:

- **권장: 하드 컷오버.** 검증기가 신 키(또는 신 kid)만 신뢰하도록 즉시 전환. 구 키로 서명된
  access·refresh 토큰은 그 즉시 검증 실패 → 자연 무효화. 사용자 영향은 "전체 재로그인" 1회.
- **보강:** refresh 토큰을 서버측 저장/회전하는 경우, 컷오버와 함께 refresh 토큰 테이블을
  전량 revoke(또는 token-version/epoch 1 증가)해 잔존 세션 재발급 경로까지 차단.
- access TTL이 15분으로 짧아 추가 차단 없이도 노출 영향 창이 작지만, 위조 위험 때문에
  TTL 만료를 기다리지 않고 **키 신뢰 중단으로 즉시 무효화**하는 것이 핵심.
- 컷오버 시점·예상 재로그인 영향(사용자 수/피크 트래픽)은 PM과 공지 일정 협의.

## 7. 히스토리 purge (별건, 게이트 deny)

노출 파일을 git 히스토리에서 제거(`git filter-repo` + force-push)하는 작업은 보안 게이트가 deny한다.
보안 검토(9d8d4e9a) 승인 후에만 진행하며, 보호 브랜치 영향/협업자 재클론 안내를 동반한다.
**키 교체(2~6장)가 1차 방어선이고, purge는 노출 흔적 제거(2차)이다 — purge 여부와 무관하게 키는 교체해야 한다.**
