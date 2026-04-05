# soo-auth-service 기존 서버 업데이트 가이드

이 문서는 **이미 운영 중인 서버**에서 `soo-auth-service`를 최신 이미지(`latest`)로 업데이트하는 방법을 다룹니다.
처음 서버를 구축하는 절차는 [README_DEPLOY_AUTOMATION.md](./README_DEPLOY_AUTOMATION.md), 개발 PC에서 Docker Hub에 이미지를 올리는 절차는 [README_DOCKER_RELEASE.md](./README_DOCKER_RELEASE.md)를 참고하십시오.

---

## 대상

- `/opt/soo-auth-service` 구조와 `compose.yaml`, `.env`, `deploy.sh`가 이미 준비된 서버
- `app` 서비스 이미지가 `${DOCKER_HUB_USERNAME}/soo-auth-service:latest`로 설정된 환경

---

## 1) 수동 업데이트 (기존 서버에서 직접)

서버 접속 후 아래 순서대로 실행합니다.

```bash
ssh deploy@<SERVER>
cd /opt/soo-auth-service
docker compose pull app
docker compose up -d --no-build app
docker compose ps
docker compose logs -f --tail=100 app
```

- `pull app`: `latest` 이미지를 새로 받습니다.
- `up -d --no-build app`: 서버 로컬 빌드 없이 새 이미지로 컨테이너를 교체합니다.

전체 서비스를 같이 갱신하려면:

```bash
cd /opt/soo-auth-service
docker compose pull
docker compose up -d --remove-orphans --no-build
```

---

## 2) 표준 배포 스크립트로 업데이트

서버의 `deploy.sh`를 아래처럼 유지하면 운영이 단순해집니다.

```bash
#!/usr/bin/env bash
set -e

cd /opt/soo-auth-service
docker compose pull
docker compose up -d --remove-orphans --no-build
docker image prune -f
```

실행:

```bash
/opt/soo-auth-service/deploy.sh
```

---

## 3) CI/CD 자동 업데이트 (GitHub Actions)

권장 흐름:

1. `main` 브랜치에 push
2. GitHub Actions가 Docker Hub에 `latest` 이미지 push
3. Actions가 SSH로 서버 접속
4. 서버에서 `/opt/soo-auth-service/deploy.sh` 실행

예시 워크플로우:

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

필수 GitHub Secrets:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `DEPLOY_HOST`
- `DEPLOY_SSH_KEY`

---

## 4) 업데이트 확인 체크리스트

```bash
cd /opt/soo-auth-service
docker compose ps
docker compose logs -f --tail=200 app
docker compose logs -f --tail=200 nginx
```

- `app`가 `Up` 상태인지 확인
- 부팅 에러(DB/Redis 연결, 환경변수 누락)가 없는지 확인
- 외부 도메인 응답(200/301/401 등 의도한 상태 코드) 확인

---

## 5) 롤백

`latest` 운영만 하면 즉시 롤백이 어렵습니다. 운영 안정성을 위해 `latest`와 함께 고정 태그도 같이 푸시하는 전략을 권장합니다.

롤백 시:

1. `compose.yaml`의 `app.image`를 문제없는 고정 태그로 변경
2. 아래 명령 실행

```bash
cd /opt/soo-auth-service
docker compose pull app
docker compose up -d --no-build app
```
