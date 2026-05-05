# 도커 로그 확인 및 관리 가이드

이 문서는 도커(Docker) 환경에서 실행 중인 서비스들의 로그를 확인하고 트러블슈팅하는 방법을 설명합니다.

## 1. 기본 로그 확인 (Docker Compose)

모든 서비스는 Docker Compose 프로파일을 통해 관리되므로, 로그 확인 시에도 해당 프로파일을 지정하는 것이 좋습니다.

### 전체 서비스 로그 확인
현재 실행 중인 모든 컨테이너의 로그를 실시간으로 확인합니다.
```bash
docker compose logs -f
```

### 특정 서비스 로그 확인
특정 컨테이너(예: `app-oauth2`, `mariadb`)의 로그만 골라서 확인합니다.
```bash
# OAuth2 서비스 로그 확인
docker compose logs -f app-oauth2

# DB 서비스 로그 확인
docker compose logs -f mariadb

# Nginx(웹 서버) 로그 확인
docker compose logs -f nginx
```

## 2. 유용한 로그 명령어 옵션

| 옵션 | 설명 | 예시 |
| :--- | :--- | :--- |
| `-f` (follow) | 로그를 실시간으로 계속 출력합니다. | `docker compose logs -f` |
| `--tail` | 마지막 N줄의 로그만 보여줍니다. | `docker compose logs --tail 100` |
| `-t` (timestamps) | 로그 출력 시 타임스탬프를 함께 표시합니다. | `docker compose logs -t` |
| `--no-color` | 흑백으로 로그를 출력합니다. (파일 저장 시 유용) | `docker compose logs --no-color > logs.txt` |

## 3. 시나리오별 로그 확인 방법

### 실시간 에러 모니터링
에러가 발생하는지 실시간으로 감시할 때 유용합니다.
```bash
docker compose logs -f | grep ERROR
```

### 컨테이너 재시작 직후 로그 확인
서비스가 뜰 때 발생하는 초기 설정 오류를 잡을 때 유용합니다.
```bash
docker compose logs --tail 50 -f app-admin
```

### 서비스별 컨테이너 이름 확인
로그를 볼 때 사용할 컨테이너 이름을 모를 경우 다음 명령어로 확인하세요.
```bash
docker compose ps
```

## 4. 고급 모니터링 (Grafana)

더 정교한 로그 분석과 시각화가 필요한 경우, 프로젝트에서 제공하는 모니터링 스택(Loki + Grafana)을 사용할 수 있습니다.

*   **가이드**: [로컬 로그 모니터링 가이드](../monitoring/README_LOG_MONITORING.md)
*   **실행**: `./deploy/deploy.sh --monitoring`

## 5. 자주 발생하는 로그 문제

*   **로그가 보이지 않음**: 컨테이너가 `Exited` 상태인지 확인하세요 (`docker compose ps`).
*   **Permission Denied**: 로그 파일 쓰기 권한 문제일 수 있습니다. (주로 로컬 볼륨 연결 시 발생)
*   **DB 연결 실패**: `mariadb` 컨테이너의 로그를 먼저 확인하여 DB가 정상적으로 `ready for connections` 상태가 되었는지 체크하세요.
