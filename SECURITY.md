# Security Policy

## Secret Management

이 저장소는 비밀번호, API key, access token, refresh token, private key를 커밋하지 않습니다.

금지 파일:

- .env
- .env.local
- .env.development
- .env.production
- *.pem
- *.key
- *.p12
- *.jks
- id_rsa
- id_ed25519
- credentials.json
- token.json
- service-account*.json

환경변수 예시는 `.env.example`에만 작성합니다.
실제 값은 로컬 환경변수, 배포 환경의 Secret Manager, GitHub Actions Secrets에만 저장합니다.

## OpenClaw Automation Rules

OpenClaw 에이전트는 다음 작업을 사람 승인 없이 수행할 수 있습니다.

- 코드 초안 작성
- 문서 작성
- git status / git diff 확인
- 빌드 실행
- 테스트 실행
- Issue 생성
- PR 초안 생성
- 리뷰 코멘트 작성

다음 작업은 반드시 사람 승인이 필요합니다.

- main/develop 직접 push
- PR merge
- production deploy
- DB migration
- .env 수정
- API key 접근
- 파일 대량 삭제
- 외부 사용자에게 메시지 발송
- 결제/환불/송금 관련 작업

## Branch Policy

기본 브랜치에 직접 push하지 않습니다.
모든 변경은 `ai/**`, `chore/**`, `feature/**` 브랜치에서 작업하고 PR로 병합합니다.

## Incident Response

민감정보가 노출된 경우:

1. 노출된 token/password/key를 즉시 폐기합니다.
2. 새 secret을 발급합니다.
3. 관련 로그와 커밋을 확인합니다.
4. 필요하면 히스토리 정리를 진행합니다.
5. 동일 사고 방지를 위해 `.gitignore`, pre-commit hook, CI 점검을 강화합니다.

## Reporting

보안 이슈는 GitHub Issue로 공개하지 말고 저장소 관리자에게 직접 전달합니다.
