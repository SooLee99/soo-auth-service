# soo-auth-service 자동화 배포 가이드 (CI/CD)

이 문서는 **서버가 완전히 비어있는 상태부터** `soo-auth-service`를 GitHub Actions로 자동 배포하는 전체 과정을 설명합니다.

구성 흐름:

1. 배포 서버 기본 패키지 설치 (git, docker, compose)
2. deploy 계정 생성
3. SSH 배포 키 생성 및 등록
4. 서버에 프로젝트 최초 배치 (`/opt/soo-auth-service`)
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

이 단계에서 사용하는 키는 **GitHub Actions가 자동으로 만들어주는 키가 아니라**, 배포용으로 **직접 생성한 SSH 키쌍**입니다.

* **public key**: 서버의 `authorized_keys` 에 등록
* **private key**: GitHub Repository Secret `DEPLOY_SSH_KEY` 에 등록

## 5-1. 배포용 SSH 키 생성

키는 로컬 PC 또는 관리용 작업 환경에서 생성합니다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/soo-auth-service-deploy -C "soo-auth-service-deploy"
```

생성 후 아래 두 파일이 생깁니다.

```bash
~/.ssh/soo-auth-service-deploy
~/.ssh/soo-auth-service-deploy.pub
```

의미는 다음과 같습니다.

* `~/.ssh/soo-auth-service-deploy` → private key
* `~/.ssh/soo-auth-service-deploy.pub` → public key

public key 확인

```bash
cat ~/.ssh/soo-auth-service-deploy.pub
```

private key 확인

```bash
cat ~/.ssh/soo-auth-service-deploy
```

## 5-2. 서버에 public key 등록

deploy 계정 상태에서 실행

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
```

authorized_keys 생성

```bash
nano ~/.ssh/authorized_keys
```

여기에 **`~/.ssh/soo-auth-service-deploy.pub` 내용 전체**를 붙여넣습니다.

예시 형태

```text
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... soo-auth-service-deploy
```

권한 설정

```bash
chmod 600 ~/.ssh/authorized_keys
```

## 5-3. 접속 테스트

로컬에서 private key를 사용해 접속이 되는지 먼저 확인합니다.

```bash
ssh -i ~/.ssh/soo-auth-service-deploy deploy@<SERVER_IP>
```

정상 접속이 되면 GitHub Actions에서도 같은 private key를 사용할 수 있습니다.

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

운영 환경 값으로 수정합니다.

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
cd /opt/soo-auth-service
docker compose ps
```

---

# 11. GitHub Secrets 설정

Repository → Settings → Secrets → Actions

다음 값 추가

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
DEPLOY_HOST
DEPLOY_PORT
DEPLOY_USER
DEPLOY_SSH_KEY
```

예시

```text
DEPLOY_HOST = 12.34.56.78
DEPLOY_PORT = 22
DEPLOY_USER = deploy
```

`DEPLOY_SSH_KEY` 에는 **5단계에서 생성한 private key 전체 내용**을 넣습니다.

```text
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

정리:

* 서버 `~/.ssh/authorized_keys` 에 넣는 값 → public key (`.pub`)
* GitHub Secret `DEPLOY_SSH_KEY` 에 넣는 값 → private key

---

# 12. GitHub Actions CD 설정

파일 생성

```text
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

자동 배포가 실행됩니다.

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

`docker-compose.yml` 수정

```text
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

```text
/opt/soo-auth-service
 ├── docker-compose.yml
 ├── .env
 ├── deploy.sh
 └── 기타 소스
```

배포 흐름

```text
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
