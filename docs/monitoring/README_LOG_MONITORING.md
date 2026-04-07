# 로컬 로그 모니터링 가이드 (Loki + Promtail + Grafana)

이 문서는 로컬에서 `core-api` 로그를 Grafana에서 확인하는 가장 간단한 절차를 제공합니다.

## 1. 모니터링 스택 실행

```bash
docker compose -f docker-compose.monitoring.yml up -d
```

접속:
- Grafana: http://localhost:3000
- ID/PW: `admin` / `admin`
- Loki health: http://localhost:3100/ready

## 2. 앱 로그 파일로 출력

Promtail은 `./logs/*.log`를 수집합니다.

```bash
mkdir -p logs
touch logs/core-api.log
./gradlew :core:core-api:bootRun 2>&1 | tee -a logs/core-api.log
```

이미 도커로 앱을 실행 중이면, 앱 컨테이너 로그를 파일로 리다이렉트하는 별도 설정이 필요합니다.

## 3. Grafana에서 로그 조회

1. Grafana 로그인 (`admin`/`admin`)
2. 왼쪽 메뉴 `Explore`
3. 데이터소스 `Loki` 선택
4. 아래 쿼리 실행

```logql
{job="soo-auth"}
```

추가 필터 예시:

```logql
{job="soo-auth"} |= "ERROR"
```

```logql
{job="soo-auth"} |= "ServiceContextResolver"
```

## 4. 메트릭 확인 (Actuator/Prometheus 노출)

앱이 실행 중이면 아래 확인 가능:

```bash
curl -s http://localhost:8080/actuator/prometheus | head
```

## 5. 종료

```bash
docker compose -f docker-compose.monitoring.yml down
```

