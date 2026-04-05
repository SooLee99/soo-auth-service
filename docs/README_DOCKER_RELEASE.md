# soo-auth-service Docker Hub 릴리즈 가이드 (개발 PC 전용)

이 문서는 **개발 컴퓨터에서 Docker Hub에 이미지를 올리고 릴리즈 태그를 갱신하는 방법**만 다룹니다.
서버 초기 세팅/배포 자동화는 [README_DEPLOY_AUTOMATION.md](./README_DEPLOY_AUTOMATION.md)를 참고하십시오.

---

## 대상

- 로컬(개발 PC)에서 직접 이미지 빌드/푸시가 필요한 경우
- 특정 버전 태그(예: `v1.2.0`, `2026.04.05`)를 수동으로 릴리즈하는 경우
- `latest` 태그를 새 버전으로 갱신하는 경우

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
docker login
```

- 사용자명/비밀번호(또는 Access Token) 입력 후 로그인합니다.

---

## 2) 이미지 빌드

아래 `<tag>`에는 원하는 릴리즈 태그를 넣습니다.

```bash
docker build -t <dockerhub-username>/soo-auth-service:<tag> .
```

예시:

```bash
docker build -t myname/soo-auth-service:v1.0.3 .
```

---

## 3) 버전 태그 푸시

```bash
docker push <dockerhub-username>/soo-auth-service:<tag>
```

예시:

```bash
docker push myname/soo-auth-service:v1.0.3
```

---

## 4) latest 태그 업데이트(선택)

운영에서 `latest`를 사용 중이라면, 릴리즈 태그를 `latest`로도 갱신합니다.

```bash
docker tag <dockerhub-username>/soo-auth-service:<tag> <dockerhub-username>/soo-auth-service:latest
docker push <dockerhub-username>/soo-auth-service:latest
```

예시:

```bash
docker tag myname/soo-auth-service:v1.0.3 myname/soo-auth-service:latest
docker push myname/soo-auth-service:latest
```

---

## 5) 푸시 확인

```bash
docker image ls | findstr soo-auth-service
```

- Docker Hub 웹 리포지토리에서 `<tag>`, `latest`가 반영되었는지 확인합니다.

---

## 권장 태그 전략

- 고정 버전 태그를 기본으로 사용: `v1.0.3`, `v2026.04.05`
- `latest`는 선택적으로만 갱신
- 롤백을 위해 의미 있는 태그를 유지

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
docker build -t myname/soo-auth-service:v1.0.3 .
docker push myname/soo-auth-service:v1.0.3
docker tag myname/soo-auth-service:v1.0.3 myname/soo-auth-service:latest
docker push myname/soo-auth-service:latest
```
