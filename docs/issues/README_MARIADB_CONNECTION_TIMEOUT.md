# 🚨 MariaDB 연결 실패 원인 보고서 (HikariCP 타임아웃 설정 과도)

## 📌 문제 요약

운영 중 애플리케이션에서 주기적으로 DB 연결 실패가 발생하며, 세션 정리 스케줄러와 트랜잭션 시작이 반복적으로 실패함.

대표 오류:

```text
core-db-pool - Connection is not available, request timed out after 1100ms
Could not connect to address=(host=mariadb)(port=3306)(type=primary) : Read timed out
Caused by: java.net.SocketTimeoutException: Read timed out
```

또한 스케줄러 작업에서도 동일한 DB 연결 실패가 반복됨:

```text
JdbcIndexedSessionRepository.cleanUpExpiredSessions
CannotCreateTransactionException
JDBCConnectionException
```

앱 로그상 커넥션 풀 상태는 계속 아래와 같았음:

```text
(total=0, active=0, idle=0, waiting=0)
```

---

## 🔎 원인 분석

초기에는 `UnknownHostException: mariadb` 로그 때문에 Docker 네트워크/DNS 문제 가능성을 의심했으나, 실제 점검 결과:

- `soo-auth-app-1` 와 `soo-auth-mariadb-1` 는 같은 `soo-auth_backend` 네트워크에 연결되어 있었음
- `getent hosts mariadb` 결과로 `mariadb` hostname 해석도 정상 확인됨
- `soo-auth-mariadb-1` 컨테이너는 `running true false 0` 상태로 재시작 없이 정상 동작 중이었음

즉, 지속적인 DNS 문제나 컨테이너 다운 문제가 아니라 애플리케이션의 DB 연결 타임아웃 설정이 지나치게 짧은 것이 핵심 원인으로 판단됨.

실제 설정 파일에는 운영 프로필에서 다음 값이 설정되어 있었음:

```yaml
maximum-pool-size: 5
connection-timeout: 1100
validation-timeout: 1000
data-source-properties:
  socketTimeout: 3000
```

이 설정은 컨테이너 환경에서 MariaDB 연결 생성 및 초기 handshake 응답을 기다리기에 너무 짧음.

---

## 📊 장애 발생 메커니즘

문제는 아래 순서로 발생함:

1. 앱이 MariaDB에 새 커넥션 생성 시도
2. MariaDB handshake 응답이 1초 내 도착하지 않음
3. HikariCP가 `connection-timeout: 1100ms` 로 먼저 포기
4. 커넥션 풀이 생성되지 못하고 `total=0` 상태 유지
5. 스케줄러/세션 저장/트랜잭션 시작 시마다 연결 실패 반복
6. MariaDB 측에는 비정상 종료된 연결이 다수 기록됨

앱 로그:

```text
request timed out after 1100ms
request timed out after 1217ms
request timed out after 1420ms
```

MariaDB 로그:

```text
Aborted connection ... user: 'soo' ... (Got an error reading communication packets)
Aborted connection ... user: 'unauthenticated' ... (This connection closed normally without authentication)
```

즉, DB 서버 자체가 죽은 것이 아니라 앱이 연결 생성 중 먼저 timeout으로 포기하면서 비정상 연결 로그가 누적된 상황임.

---

## ✅ 확인 결과

### 1. Docker 네트워크 정상

- `soo-auth-app-1`
- `soo-auth-mariadb-1`

두 컨테이너 모두 동일한 `soo-auth_backend` 네트워크에 존재함.

### 2. MariaDB 컨테이너 정상

```text
running true false 0
```

즉:

- 실행 중
- 재시작 아님
- RestartCount = 0

### 3. DB URL 주입 정상

실제 앱 컨테이너 환경변수:

```text
STORAGE_DATABASE_CORE_DB_URL=mariadb:3306/soo_auth
```

그리고 설정 파일에서 이를 다음처럼 조합하고 있었음:

```yaml
jdbc-url: jdbc:mariadb://${storage.database.core-db.url}
```

최종 JDBC URL은 정상적으로 `jdbc:mariadb://mariadb:3306/soo_auth` 형태가 됨.

---

## 🧹 해결 방법

### 1. HikariCP 타임아웃 상향

기존:

```yaml
connection-timeout: 1100
validation-timeout: 1000
socketTimeout: 3000
```

수정:

```yaml
connection-timeout: 10000
validation-timeout: 5000
data-source-properties:
  connectTimeout: 10000
  socketTimeout: 30000
```

### 2. minimum-idle / initialization-fail-timeout 추가

초기 풀 생성 안정성을 위해 다음 설정 추가:

```yaml
minimum-idle: 2
initialization-fail-timeout: 60000
```

### 3. live 프로필에도 명시적 override 적용

운영에서는 pool size만 늘리는 것이 아니라 timeout 값도 함께 명시적으로 override 해야 함.

---

## 🛠 수정된 권장 설정 예시

```yaml
storage:
  datasource:
    core:
      driver-class-name: org.mariadb.jdbc.Driver
      jdbc-url: jdbc:mariadb://${storage.database.core-db.url}
      username: ${storage.database.core-db.username}
      password: ${storage.database.core-db.password}
      minimum-idle: 2
      maximum-pool-size: 10
      connection-timeout: 10000
      validation-timeout: 5000
      initialization-fail-timeout: 60000
      keepalive-time: 30000
      max-lifetime: 600000
      pool-name: core-db-pool
      data-source-properties:
        connectTimeout: 10000
        socketTimeout: 30000
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        useLocalSessionState: true
        rewriteBatchedStatements: true
        cacheResultSetMetadata: true
        cacheServerConfiguration: true
        elideSetAutoCommits: true
        maintainTimeStats: false
```

운영 프로필:

```yaml
storage:
  datasource:
    core:
      minimum-idle: 5
      maximum-pool-size: 25
      connection-timeout: 10000
      validation-timeout: 5000
      initialization-fail-timeout: 60000
      data-source-properties:
        connectTimeout: 10000
        socketTimeout: 30000
```

---

## 🚀 적용 후 재배포

```bash
cd /opt/soo-auth-service

sudo docker compose down
sudo docker compose up -d --build
sudo docker logs -f soo-auth-app-1
```

---

## 📌 적용 후 확인 포인트

### 앱 로그에서 사라져야 하는 것

```text
request timed out after 1100ms
Could not connect to address=(host=mariadb)(port=3306) : Read timed out
Connection is not available
```

### MariaDB 로그에서 줄어들어야 하는 것

```text
Aborted connection ... unauthenticated
Aborted connection ... Got an error reading communication packets
```

### 정상 상태 기대값

- Hikari 풀에 idle connection 생성
- `total=0` 상태 해소
- 세션 cleanup 스케줄러 정상 동작
- 트랜잭션 시작 실패 제거

---

## ⚠️ 추가 개선 권장 사항

현재 장애는 `JdbcIndexedSessionRepository.cleanUpExpiredSessions` 에서 반복적으로 표면화되었음. 즉 세션 저장소가 JDBC 기반이라 DB 장애가 세션 처리 실패로 바로 이어짐.

장기적으로는 다음도 검토 권장:

- 세션 저장소를 Redis로 전환
- DB 헬스체크 및 readiness 강화
- DB 연결 재시도/backoff 정책 정비

---

## 🧾 결론

이번 이슈의 직접 원인은:

```text
운영 환경에서 HikariCP의 connection-timeout / validation-timeout / socketTimeout 값이 지나치게 짧게 설정되어,
MariaDB 연결 생성 및 handshake 단계에서 앱이 먼저 timeout으로 포기한 것
```

정리하면:

- Docker 네트워크 문제 아님
- MariaDB 컨테이너 다운 아님
- 인증 정보 문제 아님
- 애플리케이션의 DB 커넥션 타임아웃 설정 문제

핵심 해결:

```text
connection-timeout 1100ms → 10000ms
validation-timeout 1000ms → 5000ms
socketTimeout 3000ms → 30000ms
connectTimeout 10000ms 추가
```

이후 DB 연결 안정성 개선 가능.
