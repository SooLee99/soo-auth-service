# 🚨 Docker 배포 실패 원인 보고서 (디스크 용량 부족)

## 📌 문제 요약

Docker Compose 실행 중 다음 오류가 발생하며 배포가 실패함:

```text
no space left on device
failed to create prepare snapshot dir
/var/lib/containerd/... snapshots ...
```

또한 이미지 pull 단계에서 중단됨:

```text
Image soolee99/auth-admin-frontend:latest Pulling
Image grafana/grafana:11.1.0 Pulling
```

---

## 🔎 원인 분석

해당 오류는 서버 디스크 용량 부족으로 인해 Docker가 이미지 레이어를 풀지 못하면서 발생함.

Docker는 이미지 pull 시 아래 경로에 데이터를 저장함:

```text
/var/lib/docker
/var/lib/containerd
```

이 디렉터리 공간이 부족하면 다음 작업이 실패함:

- `docker compose pull`
- `docker compose up`
- container snapshot 생성
- image extraction

---

## 📊 확인 방법

### 1. 디스크 용량 확인

```bash
df -h
```

### 2. Docker 사용 용량 확인

```bash
docker system df
```

### 3. Docker 저장소 용량 확인

```bash
sudo du -sh /var/lib/docker
sudo du -sh /var/lib/containerd
```

---

## 🧹 해결 방법

### 0. 최악의 상황
```aiignore
sudo systemctl kill docker.service
sudo systemctl kill containerd.service
```

### 1. 사용하지 않는 Docker 이미지 삭제

```bash
sudo docker image prune -a
```

### 2. 중지된 컨테이너 삭제

```bash
sudo docker container prune
```

### 3. 사용하지 않는 볼륨 삭제

```bash
sudo docker volume prune
```

### 4. 전체 정리 (권장)

```bash
sudo docker system prune -a --volumes
```

---

## 🚀 정리 후 재배포

```bash
cd /opt/soo-auth-service

sudo docker compose pull
sudo docker compose up -d
```

---

## ⚠️ 추가 경고 (환경변수 미설정)

다음 경고도 발생함:

```text
The "GRAFANA_ADMIN_PASSWORD" variable is not set
```

`.env` 파일에 추가 필요:

```dotenv
GRAFANA_ADMIN_PASSWORD=your_password
```

---

## 📦 권장 최소 디스크 용량

Docker + DB + Redis + Grafana 구성 기준:

| 항목 | 권장 |
| --- | --- |
| OS | 5GB |
| Docker images | 5GB |
| MariaDB data | 2GB+ |
| Logs | 1GB |
| 여유 공간 | 5GB |

최소 권장 용량:

```text
20GB 이상
```

---

## 🧾 결론

배포 실패 원인:

```text
Docker 이미지 pull 중 디스크 공간 부족
```

해결:

```bash
docker system prune -a
```

이후 정상 배포 가능.
