# 자동화 배포 가이드 (CI/CD)

이 문서는 `soo-auth-service`를 GitHub Actions로 자동 배포하는 기준안을 설명합니다.

## 1) 목표 배포 흐름

1. PR/Push 시 CI 실행(테스트/린트)
2. `main` 브랜치 반영 시 Docker 이미지 빌드
3. Docker Hub 푸시
4. 배포 서버 SSH 접속 후 `docker compose pull && up -d`

## 2) 준비물

- GitHub Repository
- Docker Hub 계정/리포지토리
- 배포 서버(리눅스, Docker/Compose 설치 완료)
- 배포 서버에 이미 이 프로젝트가 `/opt/soo-auth-service`에 배치되어 있어야 함

## 3) 서버 1회 초기 설정

### 3-1) deploy 계정 생성
```bash
sudo adduser deploy
sudo usermod -aG docker deploy
```

### 3-2) SSH 키 등록
```bash
sudo -u deploy mkdir -p /home/deploy/.ssh
sudo -u deploy chmod 700 /home/deploy/.ssh
sudo -u deploy bash -c 'cat >> /home/deploy/.ssh/authorized_keys'
sudo -u deploy chmod 600 /home/deploy/.ssh/authorized_keys
```

### 3-3) 배포 스크립트 준비
```bash
sudo -u deploy mkdir -p /opt/soo-auth-service
sudo -u deploy bash -c 'cat > /opt/soo-auth-service/deploy.sh' <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

cd /opt/soo-auth-service
git fetch --all
git reset --hard origin/main
docker compose pull
docker compose up -d --remove-orphans
docker image prune -f
EOF

sudo chmod +x /opt/soo-auth-service/deploy.sh
```

## 4) GitHub Secrets 설정

Repository Settings -> Secrets and variables -> Actions -> New repository secret

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `DEPLOY_HOST` (예: `12.34.56.78`)
- `DEPLOY_PORT` (예: `22`)
- `DEPLOY_USER` (예: `deploy`)
- `DEPLOY_SSH_KEY` (private key 전체)

## 5) GitHub Actions 배포 워크플로우 예시

아래 파일을 추가하면 `main` push 시 자동 배포됩니다.

파일: `.github/workflows/cd-deploy.yml`

```yaml
name: CD Deploy

on:
  push:
    branches: [ "main" ]

permissions:
  contents: read

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and Push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ secrets.DOCKERHUB_USERNAME }}/soo-auth-service:latest
            ${{ secrets.DOCKERHUB_USERNAME }}/soo-auth-service:${{ github.sha }}

      - name: Remote Deploy by SSH
        uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: ${{ secrets.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          port: ${{ secrets.DEPLOY_PORT }}
          script: |
            /opt/soo-auth-service/deploy.sh
```

## 6) 배포 확인 명령어

```bash
docker compose ps
docker compose logs -f app
curl -i http://127.0.0.1:8080/health
```

## 7) 롤백 방법

### 방법 A: 이전 커밋 재배포
```bash
cd /opt/soo-auth-service
git log --oneline -n 5
git checkout <이전_정상_커밋>
docker compose up -d --build
```

### 방법 B: 특정 이미지 태그로 고정
`docker-compose.yml`의 app 이미지 태그를 `latest` 대신 `sha` 태그로 지정 후 재기동:
```bash
docker compose pull
docker compose up -d
```

## 8) 권장 운영 규칙

- `main` 보호 브랜치 + PR 승인 후 머지
- 배포 전 CI 통과 필수
- 운영 반영은 태그 버전 기준 권장(`latest` 단독 사용 지양)
- DB 마이그레이션은 배포 파이프라인과 분리해 단계적으로 수행
