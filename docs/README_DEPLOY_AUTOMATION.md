# soo-auth-service 자동화 배포 가이드

이 문서는 **서버가 완전히 비어있는 상태부터**
`soo-auth-service`를 **Docker Hub + GitHub Actions + SSH + Docker Compose + Nginx** 방식으로 자동 배포하는 전체 과정을 설명합니다.

<br>

**[문서 기준 배포 방식]**

* GitHub Actions → Docker Hub 이미지 push
* 서버 → `docker compose pull`
* ARM / Intel 자동 지원
* keystore 외부 관리
* Nginx reverse proxy 구성
* HTTP/HTTPS 확장 가능 구조

---

# 전체 배포 흐름

```text
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
   ↓
nginx가 app으로 reverse proxy
```

1. 코드를 GitHub에 push
2. GitHub가 Docker 이미지를 생성
3. Docker Hub에 업로드
4. 서버가 새 이미지를 pull
5. `deploy.sh` 실행으로 컨테이너 재기동
6. nginx가 외부 요청을 받아 app으로 전달

---

# 서버 디렉터리 구조

```text
/opt/soo-auth-service
 ├── compose.yaml
 ├── .env
 ├── deploy.sh
 ├── nginx
 │   ├── nginx.conf
 │   ├── conf.d
 │   │   └── default.conf
 │   ├── ssl
 │   │   ├── fullchain.crt
 │   │   └── private.key
 │   └── logs
 ├── keys
 │   └── jwt-keystore.p12
 └── logs
```

* `compose.yaml`
  전체 컨테이너 구성을 정의합니다.
* `.env`
  비밀번호, 환경변수 값을 저장합니다.
* `deploy.sh`
  실제 서버 배포 스크립트입니다.
* `nginx/nginx.conf`
  nginx 공통 설정입니다.
* `nginx/conf.d/default.conf`
  도메인별 reverse proxy 설정입니다.
* `nginx/ssl/`
  SSL 인증서와 개인키를 둡니다.
* `keys/`
  JWT keystore 파일을 둡니다.

---

# 1. 서버 초기 세팅

```bash
sudo apt update
sudo apt upgrade -y
```

설명:

* 패키지 목록을 최신 상태로 갱신합니다.
* 기본 보안 업데이트를 적용합니다.

---

# 2. Docker 설치

```bash
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings

sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
-o /etc/apt/keyrings/docker.asc

sudo chmod a+r /etc/apt/keyrings/docker.asc
```

```bash
echo \
"deb [arch=$(dpkg --print-architecture) \
signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu \
$(. /etc/os-release && echo $VERSION_CODENAME) stable" \
| sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

```bash
docker --version
docker compose version
```

* Docker 공식 저장소를 등록한 뒤 설치합니다.
* `docker compose` 플러그인을 함께 설치합니다.
* 이후 서버는 직접 빌드하지 않고 이미지 pull 중심으로 동작합니다.

---

# 3. deploy 계정 생성

```bash
sudo adduser deploy
sudo usermod -aG docker deploy
sudo su - deploy
```

비밀번호 설정:

```bash
sudo passwd deploy
```

* `root` 대신 배포 전용 계정을 사용합니다.
* `docker` 그룹에 넣어야 docker 명령을 실행할 수 있습니다.

---

# 4. SSH 배포 키 생성

로컬 PC:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/soo-auth-deploy
```

public key 확인:

```bash
cat ~/.ssh/soo-auth-deploy.pub
```

서버 등록:

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
nano ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

* GitHub Actions가 서버에 접속하려면 SSH 키가 필요합니다.
* 공개키는 서버에 등록하고, 개인키는 GitHub Secret에 넣습니다.

---

# 5. 배포 디렉터리 생성

```bash
sudo mkdir -p /opt/soo-auth-service
sudo chown -R deploy:deploy /opt/soo-auth-service
```

```bash
cd /opt/soo-auth-service
```

* 배포 관련 파일을 한 곳에 모아 관리합니다.
* 이후 모든 작업은 이 디렉터리 기준으로 진행합니다.

---

# 6. 디렉터리 생성

```bash
mkdir -p nginx/conf.d
mkdir -p nginx/ssl
mkdir -p nginx/logs
mkdir -p keys
mkdir -p logs
```

* nginx 설정, 인증서, 로그, keystore 위치를 미리 만듭니다.
* 배포 후 파일 위치를 헷갈리지 않게 하기 위한 준비 단계입니다.

---

# 7. compose.yaml 작성

```bash
nano /opt/soo-auth-service/compose.yaml
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
      TZ: Asia/Seoul
      MARIADB_ROOT_PASSWORD: ${MARIADB_ROOT_PASSWORD}
      MARIADB_DATABASE: ${MARIADB_DATABASE}
      MARIADB_USER: ${MARIADB_USER}
      MARIADB_PASSWORD: ${MARIADB_PASSWORD}
    volumes:
      - mariadb_data:/var/lib/mysql
    networks:
      backend:
        aliases:
          - mariadb
    healthcheck:
      test: ["CMD-SHELL", "mariadb-admin ping -h 127.0.0.1 -uroot -p$$MARIADB_ROOT_PASSWORD --silent"]
      interval: 5s
      timeout: 3s
      retries: 30
      start_period: 10s

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis_data:/data
    networks:
      backend:
        aliases:
          - redis
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20
      start_period: 5s

  phpmyadmin:
    image: phpmyadmin:latest
    restart: unless-stopped
    environment:
      TZ: Asia/Seoul
      PMA_HOST: mariadb
      PMA_ARBITRARY: 0
    ports:
      - "127.0.0.1:8081:80"
    depends_on:
      mariadb:
        condition: service_healthy
    networks:
      - backend

  app:
    image: ${DOCKER_HUB_USERNAME}/soo-auth-service:latest
    restart: unless-stopped
    expose:
      - "8080"
    environment:
      TZ: Asia/Seoul
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
      mariadb:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - backend

  nginx:
    image: nginx:1.27-alpine
    restart: unless-stopped
    depends_on:
      - app
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - ./nginx/logs:/var/log/nginx
    networks:
      - backend
```

### mariadb

* 서비스의 데이터베이스입니다.
* volume을 사용해 컨테이너 재생성 후에도 데이터가 유지됩니다.
* healthcheck로 DB 준비 전에 app이 먼저 뜨는 문제를 줄입니다.

### redis

* 캐시 또는 세션 저장용입니다.
* volume을 사용해 데이터를 유지합니다.

### phpmyadmin

* DB 관리 도구입니다.
* `127.0.0.1:8081:80` 으로 바인딩해 외부 인터넷에 바로 노출되지 않게 합니다.

### app

* 실제 Spring 애플리케이션입니다.
* `ports` 대신 `expose`만 사용합니다.
* 즉 외부 사용자는 app에 직접 접근하지 않고 nginx를 통해 접근합니다.

### nginx

* 외부 요청을 받는 웹 서버입니다.
* 80, 443 포트를 담당합니다.
* HTTPS 인증서도 여기에 연결합니다.

---

# 8. nginx.conf 작성

```bash
nano /opt/soo-auth-service/nginx/nginx.conf
```

```nginx
user  nginx;
worker_processes  auto;

events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    sendfile on;
    keepalive_timeout 65;
    server_tokens off;

    client_max_body_size 20m;

    access_log /var/log/nginx/access.log;
    error_log  /var/log/nginx/error.log;

    include /etc/nginx/conf.d/*.conf;
}
```

* nginx의 공통 설정입니다.
* `server_tokens off;` 는 nginx 버전 노출을 줄입니다.
* 실제 도메인 설정은 `conf.d/*.conf` 에서 불러옵니다.

---

# 9. default.conf 작성

```bash
nano /opt/soo-auth-service/nginx/conf.d/default.conf
```

인증서 적용 전 HTTP 설정:

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN YOUR_WWW_DOMAIN;

    location / {
        proxy_pass http://app:8080;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

인증서 적용 후 HTTPS 설정:

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN YOUR_WWW_DOMAIN;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name YOUR_DOMAIN YOUR_WWW_DOMAIN;

    ssl_certificate /etc/nginx/ssl/fullchain.crt;
    ssl_certificate_key /etc/nginx/ssl/private.key;

    location / {
        proxy_pass http://app:8080;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

* `server_name` 에는 실제 도메인을 넣습니다.
* nginx가 외부 요청을 받아 `app:8080` 으로 전달합니다.
* HTTPS 설정에서는 80 포트를 443으로 리다이렉트합니다.

---

# 10. .env 작성

```bash
nano /opt/soo-auth-service/.env
```

* `.env` 에는 실제 운영 환경값을 넣습니다.

---

# 11. keystore 업로드

```bash
scp jwt-keystore.p12 deploy@SERVER:/opt/soo-auth-service/keys/
```

* JWT 서명용 keystore를 서버 외부 파일로 관리합니다.
* 이미지 안에 포함하지 않고 서버에 따로 두는 방식입니다.

---

# 12. SSL 인증서 업로드

```bash
scp fullchain.crt deploy@SERVER:/opt/soo-auth-service/nginx/ssl/
scp private.key deploy@SERVER:/opt/soo-auth-service/nginx/ssl/
```

권한 설정:

```bash
chmod 600 /opt/soo-auth-service/nginx/ssl/private.key
chmod 644 /opt/soo-auth-service/nginx/ssl/fullchain.crt
```

* 인증서와 개인키는 nginx가 읽을 수 있는 위치에 둡니다.
* `private.key` 는 민감하므로 권한을 좁게 설정합니다.

---

# 13. deploy.sh 작성

```bash
nano /opt/soo-auth-service/deploy.sh
```

```bash
#!/usr/bin/env bash
set -e

cd /opt/soo-auth-service

docker compose pull
docker compose up -d --remove-orphans
docker image prune -f
```

권한 부여:

```bash
chmod +x /opt/soo-auth-service/deploy.sh
```

* 최신 이미지를 pull 하고
* 컨테이너를 최신 상태로 다시 띄우고
* 오래된 이미지를 정리합니다.

---

# 14. 최초 실행

```bash
cd /opt/soo-auth-service
docker compose up -d
```

* 자동배포 연결 전, 서버에서 직접 한 번 실행해 정상 구동을 확인합니다.

---

# 15. 상태 확인

```bash
docker compose ps
```

```bash
docker compose logs -f app
```

```bash
docker compose logs -f nginx
```

* `ps` 는 컨테이너 상태 확인
* `app` 로그는 애플리케이션 부팅 확인
* `nginx` 로그는 프록시/포트 문제 확인에 사용합니다.

---

# 16. 자동 배포 실행

GitHub Actions에서 `main` 브랜치 push 시 자동 실행되도록 구성합니다.

수동 확인이 필요할 때 서버에서 직접 실행:

```bash
/opt/soo-auth-service/deploy.sh
```

* 자동배포가 정상이라면 `git push` 후 GitHub Actions가 서버에 접속해 이 스크립트를 실행합니다.
* 문제 확인 시 서버에서 직접 실행해도 됩니다.

---

# 17. GitHub Actions 배포 파일

파일 경로:

```text
.github/workflows/deploy.yml
```

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

* GitHub가 이미지를 빌드하고 Docker Hub에 push 합니다.
* 이후 SSH로 서버에 접속해서 `deploy.sh` 를 실행합니다.

---

# 18. ARM / Intel 자동 지원

GitHub Actions 설정:

```yaml
platforms: linux/amd64,linux/arm64
```

* Intel 서버와 ARM 서버 모두 같은 방식으로 배포할 수 있습니다.
* Docker Hub에서 서버 아키텍처에 맞는 이미지를 자동 선택합니다.

---

# 19. 롤백

특정 이미지 태그로 되돌릴 때:

```yaml
image: <dockerhub>/soo-auth-service:<sha>
```

그 후 서버에서:

```bash
cd /opt/soo-auth-service
docker compose pull
docker compose up -d
```

* 최신 버전에 문제가 있을 경우 이전 태그로 되돌릴 수 있습니다.

---

# 최종 배포 흐름

```text
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
↓
nginx reverse proxy
↓
외부 접속
```

---

# 운영 시 체크할 것

* 보안그룹에서 80, 443, 22 포트 허용
* 도메인의 A 레코드가 서버 IP를 가리키는지 확인
* SSL 인증서 발급 전에는 HTTP로 먼저 테스트
* SSL 인증서 적용 후에는 nginx 재시작

nginx 재시작:

```bash
cd /opt/soo-auth-service
docker compose restart nginx
```

---
