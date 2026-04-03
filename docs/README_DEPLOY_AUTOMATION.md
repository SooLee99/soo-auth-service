# soo-auth-service 자동화 배포 가이드

이 문서는 **서버가 완전히 비어있는 상태부터**
`soo-auth-service`를 **Docker Hub + GitHub Actions + SSH** 방식으로 자동 배포하는 전체 과정을 설명합니다.

이 문서 기준 배포 방식:

* GitHub Actions → Docker Hub 이미지 push
* 서버 → docker compose pull
* ARM / Intel 자동 지원
* keystore 외부 관리
* 포트포워딩 접속 지원

---

# 전체 배포 흐름

```
git push
   ↓
GitHub Actions
   ↓
멀티 아키텍처 Docker build
   ↓
Docker Hub push
   ↓
SSH 서버 접속
   ↓
deploy.sh 실행
   ↓
docker compose pull
   ↓
컨테이너 재시작
```

---

# 서버 디렉터리 구조

```
/opt/soo-auth-service
 ├── docker-compose.yml
 ├── .env
 ├── deploy.sh
 ├── keys
 │   └── jwt-keystore.p12
 └── logs
```

---

# 1. 서버 초기 세팅

```
sudo apt update
sudo apt upgrade -y
```

---

# 2. Docker 설치

```
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings

sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
-o /etc/apt/keyrings/docker.asc

sudo chmod a+r /etc/apt/keyrings/docker.asc
```

```
echo \
"deb [arch=$(dpkg --print-architecture) \
signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu \
$(. /etc/os-release && echo $VERSION_CODENAME) stable" \
| sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

```
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

확인

```
docker --version
docker compose version
```

---

# 3. deploy 계정 생성

```
sudo adduser deploy
sudo usermod -aG docker deploy
sudo su - deploy
```

비밀번호 설정

```
sudo passwd deploy
```

---

# 4. SSH 배포 키 생성

로컬 PC

```
ssh-keygen -t ed25519 -f ~/.ssh/soo-auth-deploy
```

public key 확인

```
cat ~/.ssh/soo-auth-deploy.pub
```

서버 등록

```
mkdir -p ~/.ssh
chmod 700 ~/.ssh
nano ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

---

# 5. 배포 디렉터리 생성

```
sudo mkdir -p /opt/soo-auth-service
sudo chown -R deploy:deploy /opt/soo-auth-service
```

---

# 6. docker-compose.yml 작성

```
nano /opt/soo-auth-service/docker-compose.yml
```

```yaml
name: soo-auth

networks:
  backend:
    driver: bridge

volumes:
  mariadb_data:
  redis_data:

services:

  mariadb:
    image: mariadb:11.2
    restart: unless-stopped
    environment:
      MARIADB_ROOT_PASSWORD: ${MARIADB_ROOT_PASSWORD}
      MARIADB_DATABASE: ${MARIADB_DATABASE}
      MARIADB_USER: ${MARIADB_USER}
      MARIADB_PASSWORD: ${MARIADB_PASSWORD}
    volumes:
      - mariadb_data:/var/lib/mysql
    networks:
      - backend

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    volumes:
      - redis_data:/data
    networks:
      - backend

  phpmyadmin:
    image: phpmyadmin:latest
    restart: unless-stopped
    ports:
      - "8081:80"
    environment:
      PMA_HOST: mariadb
    networks:
      - backend

  app:
    image: ${DOCKER_HUB_USERNAME}/soo-auth-service:latest
    restart: unless-stopped
    ports:
      - "8080:8080"

    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}

      STORAGE_DATABASE_CORE_DB_URL: mariadb:3306/${MARIADB_DATABASE}
      STORAGE_DATABASE_CORE_DB_USERNAME: ${MARIADB_USER}
      STORAGE_DATABASE_CORE_DB_PASSWORD: ${MARIADB_PASSWORD}

      REDIS_HOST: redis
      REDIS_PORT: 6379

      JWT_KEYSTORE_PASSWORD: ${JWT_KEYSTORE_PASSWORD}
      JWT_KEY_ALIAS: ${JWT_KEY_ALIAS}
      JWT_KEY_PASSWORD: ${JWT_KEY_PASSWORD}

    depends_on:
      - mariadb
      - redis

    networks:
      - backend
```

---

# 7. .env 파일 작성

```
nano /opt/soo-auth-service/.env
```


---

# 8. keystore 업로드

```
mkdir -p /opt/soo-auth-service/keys
```

```
scp jwt-keystore.p12 <USER>@<SERVER>:/opt/soo-auth-service/keys/
```

---

# 9. 배포 스크립트

```
nano /opt/soo-auth-service/deploy.sh
```

```
#!/usr/bin/env bash
set -e

cd /opt/soo-auth-service

docker compose pull
docker compose up -d --remove-orphans
```

권한

```
chmod +x deploy.sh
```

---

# 10. GitHub Secrets

등록

```
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
DEPLOY_HOST
DEPLOY_SSH_KEY
```

---

# 11. GitHub Actions

`.github/workflows/deploy.yml`

```yaml
name: Deploy

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: docker/setup-buildx-action@v3

      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          platforms: linux/amd64,linux/arm64
          tags: |
            ${{ secrets.DOCKERHUB_USERNAME }}/soo-auth-service:latest

      - uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: deploy
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          script: |
            /opt/soo-auth-service/deploy.sh
```

---

# 12. 배포 실행

```
git push origin main
```

---

# 13. 배포 확인

```
docker compose ps
```

```
docker compose logs -f app
```

성공 로그

```
Started CoreApiApplicationKt
```

---

# 14. 외부 접속

기본 접속

```
http://<SERVER_IP>:8080
```

포트포워딩 환경

```
http://<DOMAIN>:<PORT>
```

Swagger

```
http://<DOMAIN>:<PORT>/docs/swagger/index.html
```

---

# 15. ARM / Intel 자동 지원

GitHub Actions에서 멀티아키 빌드

```
platforms: linux/amd64,linux/arm64
```

서버는 자동 선택됨

---

# 16. 롤백

docker-compose 수정

```
image: <dockerhub>/soo-auth-service:<sha>
```

```
docker compose pull
docker compose up -d
```

---

# 최종 배포 흐름

```
git push
↓
GitHub Actions
↓
Docker buildx
↓
Docker Hub
↓
SSH deploy
↓
docker compose pull
↓
서비스 재시작
```
