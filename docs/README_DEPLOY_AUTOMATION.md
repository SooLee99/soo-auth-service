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
# soo-auth-service 자동화 배포 가이드 (CI/CD)

이 문서는 **서버가 완전히 비어있는 상태부터** `soo-auth-service`를 GitHub Actions로 자동 배포하는 전체 과정을 설명합니다.

구성 흐름:

1. 배포 서버 기본 패키지 설치 (git, docker, compose)
2. deploy 계정 생성
3. SSH 키 등록
4. 서버에 프로젝트 최초 배치 (/opt/soo-auth-service)
5. 배포 스크립트 작성
6. GitHub Actions 연결
7. 자동 배포

---

# 1. 배포 서버 초기 세팅 (Ubuntu 기준)

root 또는 sudo 권한 계정으로 실행합니다.

## 1-1. 패키지 업데이트

```bash
sudo apt update
sudo apt upgrade -y
```

---

# 2. Git 설치

```bash
sudo apt install -y git
```

설치 확인

```bash
git --version
```

---

# 3. Docker 설치

공식 Docker 설치 방식 사용

```bash
sudo apt install -y ca-certificates curl gnupg lsb-release
```

GPG 키 추가

```bash
sudo mkdir -p /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
 | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
```

Docker repository 등록

```bash
echo \
"deb [arch=$(dpkg --print-architecture) \
signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu \
$(lsb_release -cs) stable" \
| sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

Docker 설치

```bash
sudo apt update

sudo apt install -y \
docker-ce \
docker-ce-cli \
containerd.io \
docker-buildx-plugin \
docker-compose-plugin
```

설치 확인

```bash
docker --version
docker compose version
```

Docker 자동 시작

```bash
sudo systemctl enable docker
sudo systemctl start docker
```

---

# 4. deploy 계정 생성

```bash
sudo adduser deploy
```

docker 사용 권한 추가

```bash
sudo usermod -aG docker deploy
```

적용

```bash
sudo su - deploy
```

---

# 5. SSH 키 등록 (GitHub Actions 접속용)

deploy 계정 상태에서 실행

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
```

authorized_keys 생성

```bash
nano ~/.ssh/authorized_keys
```

여기에 GitHub Actions에서 사용할 **public key** 붙여넣기

권한 설정

```bash
chmod 600 ~/.ssh/authorized_keys
```

---

# 6. 프로젝트 배포 디렉터리 생성

다시 root 또는 sudo 권한으로

```bash
sudo mkdir -p /opt/soo-auth-service
sudo chown -R deploy:deploy /opt/soo-auth-service
```

---

# 7. 서버에 프로젝트 최초 배치

deploy 계정으로 실행

```bash
sudo -u deploy git clone https://github.com/<OWNER>/soo-auth-service.git /opt/soo-auth-service
```

또는 SSH 사용

```bash
sudo -u deploy git clone git@github.com:<OWNER>/soo-auth-service.git /opt/soo-auth-service
```

확인

```bash
ls /opt/soo-auth-service
```

---

# 8. 환경 변수 파일 준비 (.env)

필요한 경우

```bash
cd /opt/soo-auth-service

cp .env.example .env
nano .env
```

운영 환경 값으로 수정

---

# 9. 배포 스크립트 생성

```bash
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
```

권한 부여

```bash
sudo chmod +x /opt/soo-auth-service/deploy.sh
```

---

# 10. 최초 수동 배포 테스트

```bash
sudo -u deploy /opt/soo-auth-service/deploy.sh
```

정상 동작 확인

```bash
docker compose ps
```

---

# 11. GitHub Secrets 설정

Repository → Settings → Secrets → Actions

다음 값 추가

```
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
DEPLOY_HOST
DEPLOY_PORT
DEPLOY_USER
DEPLOY_SSH_KEY
```

예시

```
DEPLOY_HOST = 12.34.56.78
DEPLOY_PORT = 22
DEPLOY_USER = deploy
```

DEPLOY_SSH_KEY 는 private key 전체 입력

```
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

---

# 12. GitHub Actions CD 설정

파일 생성

```
.github/workflows/cd-deploy.yml
```

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

---

# 13. 배포 테스트

main 브랜치 push

```bash
git push origin main
```

자동 배포 실행됨

---

# 14. 배포 확인

서버에서 확인

```bash
cd /opt/soo-auth-service

docker compose ps
```

로그 확인

```bash
docker compose logs -f
```

헬스 체크

```bash
curl -i http://127.0.0.1:8080/health
```

---

# 15. 롤백 방법

## 이전 커밋 롤백

```bash
cd /opt/soo-auth-service

git log --oneline -n 5

git checkout <commit>

docker compose up -d --build
```

## 특정 이미지 태그로 롤백

docker-compose.yml 수정

```
image: username/soo-auth-service:<sha>
```

재기동

```bash
docker compose pull
docker compose up -d
```

---

# 배포 구조

서버 디렉터리

```
/opt/soo-auth-service
 ├── docker-compose.yml
 ├── .env
 ├── deploy.sh
 └── 기타 소스
```

배포 흐름

```
GitHub push
     ↓
GitHub Actions
     ↓
Docker build & push
     ↓
SSH 접속
     ↓
deploy.sh 실행
     ↓
docker compose pull
docker compose up -d
```

## 8) 권장 운영 규칙

- `main` 보호 브랜치 + PR 승인 후 머지
- 배포 전 CI 통과 필수
- 운영 반영은 태그 버전 기준 권장(`latest` 단독 사용 지양)
- DB 마이그레이션은 배포 파이프라인과 분리해 단계적으로 수행
