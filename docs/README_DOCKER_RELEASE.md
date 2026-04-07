# soo-auth-service Docker Hub 릴리즈 가이드 (개발 PC 전용)

이 문서는 **개발 컴퓨터에서 Docker Hub에 이미지를 올리고 `latest` 태그로 항상 최신 버전을 릴리즈하는 방법**만 다룹니다.
기존 서버 업데이트 절차는 [README_SERVER_UPDATE.md](./README_SERVER_UPDATE.md), 서버 초기 세팅/배포 자동화는 [README_DEPLOY_AUTOMATION.md](./README_DEPLOY_AUTOMATION.md)를 참고하십시오.

---

## 대상

- 로컬(개발 PC)에서 직접 이미지 빌드/푸시가 필요한 경우
- Docker Hub의 `latest` 태그를 **항상 최신 버전으로 유지**해야 하는 경우

---

## 사전 준비

1. Docker Desktop(또는 Docker Engine) 설치
2. Docker Hub 계정/리포지토리 준비
3. 터미널에서 프로젝트 루트로 이동

```bash
cd <project-root>
docker --version
```

---

## 1) Docker Hub 로그인

```bash
docker login -u <dockerhub-username>
```

- 사용자명/비밀번호(또는 Access Token) 입력 후 로그인합니다.

---

## 2) 최신 이미지 빌드 (`latest`)

```bash
sudo docker build -t <dockerhub-username>/soo-auth-service:latest .
```

---

## 3) 최신 이미지 푸시 (`latest`)

```bash
sudo docker push <dockerhub-username>/soo-auth-service:latest
```

---

## 4) 푸시 확인

```bash
docker image ls | findstr soo-auth-service
```

- Docker Hub 웹 리포지토리에서 `latest`가 방금 빌드한 이미지로 반영되었는지 확인합니다.

---

## 운영 권장사항

- 이 가이드는 `latest` 고정 운영 전제입니다.
- 배포 시 항상 최신 이미지가 내려받아지도록 `docker compose pull` 또는 재배포 절차를 포함하십시오.
- 롤백이 필요할 수 있다면 별도 버전 태그 전략을 추가로 운영하십시오.

---

## 자주 발생하는 문제

### `denied: requested access to the resource is denied`

- 이미지 이름의 `<dockerhub-username>`이 본인 계정과 다른 경우가 많습니다.
- `docker login` 계정과 리포지토리 소유자를 확인하십시오.

### `unauthorized`

- 토큰 만료/권한 부족 가능성이 큽니다.
- Docker Hub Access Token을 새로 발급해 다시 로그인하십시오.

### 빌드는 되는데 실행 시 설정 오류

- 이 문서는 푸시 절차 전용입니다.
- 서버 실행 환경 변수/compose 설정은 [README_DEPLOY_AUTOMATION.md](./README_DEPLOY_AUTOMATION.md)에서 관리하십시오.

---

## 빠른 실행 예시

```bash
docker login
docker build -t myname/soo-auth-service:latest .
docker push myname/soo-auth-service:latest
```
