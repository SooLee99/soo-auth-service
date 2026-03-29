# soo-auth-service

인증 서버(로컬/소셜 로그인, 관리자 사용자 관리, SMS 관리) 프로젝트입니다.

## 문서
- 프론트엔드 개발 가이드: [docs/README_FRONTEND_API.md](docs/README_FRONTEND_API.md)
- 로컬 HTTP 테스트 가이드: [core/core-api/src/test/http/README.md](core/core-api/src/test/http/README.md)
- 로깅/모니터링: [docs/README_LOG_MONITORING.md](docs/README_LOG_MONITORING.md)
- 자동화 배포(CI/CD): [docs/README_DEPLOY_AUTOMATION.md](docs/README_DEPLOY_AUTOMATION.md)

## 신규 서버 배포 가이드 (Docker Compose)

### 1) 서버 준비
```bash
# Ubuntu 기준
sudo apt-get update
sudo apt-get install -y ca-certificates curl git

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# 재로그인 후 확인
docker --version
docker compose version
```

### 2) 소스 배포 디렉터리 준비
```bash
sudo mkdir -p /opt/soo-auth-service
sudo chown -R $USER:$USER /opt/soo-auth-service
cd /opt/soo-auth-service
git clone <YOUR_REPO_URL> .
```

### 3) 환경 변수 파일(.env) 작성
```bash
cat > .env <<'EOF'
DOCKER_HUB_USERNAME=your-dockerhub-id
```

### 4) 첫 배포
```bash
cd /opt/soo-auth-service
docker compose pull
docker compose up -d --build
```

### 5) 상태 확인
```bash
docker compose ps
docker compose logs -f app
curl -i http://127.0.0.1:8080/health
```

### 6) 운영 배포(업데이트)
```bash
cd /opt/soo-auth-service
git pull
docker compose up -d --build
docker compose ps
```

### 7) 재시작/중지
```bash
docker compose restart app
docker compose stop
docker compose start
```

### 8) 완전 종료(주의)
```bash
# 컨테이너만 내림 (데이터 유지)
docker compose down

# 컨테이너 + 볼륨 삭제 (DB/Redis 데이터 삭제)
docker compose down -v
```

## 로컬 개발 실행
```bash
./gradlew :core:core-api:bootRun
```

## 문서 재생성
```bash
./gradlew :core:core-api:generateApiDocs
```

## 자동화 배포 요약

권장 방식:
1. `main` 머지 시 GitHub Actions가 Docker 이미지를 빌드/푸시
2. Actions가 배포 서버에 SSH 접속
3. 서버에서 `docker compose pull && docker compose up -d` 실행

상세 절차/시크릿/워크플로 파일 예시는 아래 문서를 참고하세요.
- [docs/README_DEPLOY_AUTOMATION.md](docs/README_DEPLOY_AUTOMATION.md)
