# 로컬 로그 모니터링 가이드 (Loki + Promtail + Grafana)

이 문서는 로컬에서 인증 서버 로그를 Grafana에서 확인하는 절차입니다.

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
touch logs/auth-service.log
./gradlew :core:core-auth-common:bootRun 2>&1 | tee -a logs/auth-service.log
```

## 3. Grafana에서 로그 조회

1. Grafana 로그인 (`admin`/`admin`)
2. `Explore` 이동
3. 데이터소스 `Loki` 선택
4. 쿼리 실행

```logql
{job="soo-auth"}
```

추가 필터:

```logql
{job="soo-auth"} |= "ERROR"
```

## 4. 메트릭 확인

```bash
curl -s http://localhost:8080/actuator/prometheus | head
```

## 5. 종료

```bash
docker compose -f docker-compose.monitoring.yml down
```
