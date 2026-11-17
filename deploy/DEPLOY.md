# PAWPOSE 서버 배포 가이드 (BE-4)

서버 1대(EC2, 시드니 `ap-southeast-2`)에 Docker로 띄웁니다. 개발 서버 = 배포 서버입니다.

```
EC2 (m7i-flex.large, Ubuntu 24.04)
 ├─ nginx      :80   외부 공개 → backend로 전달
 ├─ backend    :8080 (내부)  Spring Boot, prod 프로필
 ├─ embedding  :8091 (내부)  SigLIP CPU 임베딩, 매칭 머지 후에만 켬
 └─ db         :5432 (내부)  PostgreSQL 16, 볼륨 pgdata
S3: pawpose-team28-s3-images  (EC2 역할로 접근, 키 불필요)
```

## 파일

| 파일 | 역할 |
|---|---|
| `Dockerfile` | Spring 빌드(JDK 17) → 실행(JRE 17) |
| `.dockerignore` | 빌드에 불필요한 파일 제외 |
| `embedding-service/Dockerfile` | Python 3.12 + CPU torch, 모델을 이미지에 미리 받음 |
| `docker-compose.yml` | db, backend, embedding, nginx |
| `deploy/nginx.conf` | 80 → backend:8080, 업로드 50MB |
| `deploy/env.server.example` | 서버 `.env` 템플릿 |

## 처음 배포

```bash
# 1) 서버 접속
ssh -i pawpose-key.pem ubuntu@<탄력적IP>

# 2) 코드 받기 (공개 레포)
git clone https://github.com/line4thon-team28/Backend.git
cd Backend

# 3) 비밀값 채우기
cp deploy/env.server.example .env
openssl rand -base64 48   # → JWT_SECRET
openssl rand -hex 24      # → DB_PASSWORD
openssl rand -hex 32      # → EMBEDDING_API_KEY
nano .env

# 4) 실행 (처음 빌드는 몇 분 걸림)
docker compose up -d --build

# 5) 확인
docker compose ps
docker compose logs -f backend    # "Started PawposeApplication" 나오면 성공
```

브라우저: `http://<탄력적IP>/swagger-ui.html`

## 코드 업데이트 (main에 머지된 뒤)

```bash
cd ~/Backend
git pull
docker compose up -d --build
```

## 매칭(F-04) 켜기 — `feat/f03-f04-matching` 이 main에 머지된 뒤

```bash
# .env 에서 MATCHING_WORKER_ENABLED=true, EMBEDDING_API_KEY 확인
git pull
docker compose --profile matching up -d --build
docker compose logs -f embedding   # 모델 로드에 20~60초
```

임베딩 이미지 첫 빌드는 torch + 모델(약 800MB) 때문에 5~10분 걸립니다.
이후 업데이트 때도 `--profile matching` 을 붙여야 embedding이 같이 유지됩니다.

## 자주 쓰는 명령

```bash
docker compose ps                     # 상태
docker compose logs -f backend        # 로그
docker compose restart backend        # 재시작
docker stats --no-stream              # 메모리 사용량
```

## DB 백업 / 복원

```bash
# 백업
docker compose exec -T db pg_dump -U pawpose pawpose > backup_$(date +%m%d_%H%M).sql
aws s3 cp backup_*.sql s3://pawpose-team28-s3-images/backups/   # 선택

# 복원
docker compose exec -T db psql -U pawpose pawpose < backup_XXXX.sql
```

`docker compose down -v` 는 DB 볼륨까지 지웁니다. 데이터를 지울 때가 아니면 `-v` 를 쓰지 마세요.

## 주의

- prod 프로필에서는 `LocalDataInitializer`(데모 계정·샘플 공고)가 실행되지 않습니다. 서버 DB는 처음에 비어 있습니다.
- 카카오 Redirect URI는 FE 주소입니다. FE를 배포하면 카카오 디벨로퍼스, FE, 서버 `.env`의 `KAKAO_REDIRECT_URI` 세 곳을 같은 값으로 바꾸고, `CORS_ALLOWED_ORIGINS` 에도 FE 주소를 추가합니다.
- 보안 그룹(`pawpose-sg`): 22·80·443 전체 허용, 8080은 닫힘. SSH는 `.pem` 키로만 로그인되므로 키가 새지 않게 관리하고, 제출 후에는 22를 관리자 IP로 좁힙니다.
- 서버에 배포 파일을 `scp`로 먼저 복사해 둔 경우, 이 파일들이 main에 머지된 뒤 처음 `git pull` 하기 전에 복사본을 지워야 합니다.
  ```bash
  rm -rf Dockerfile .dockerignore docker-compose.yml deploy embedding-service
  git pull && docker compose up -d --build
  ```
