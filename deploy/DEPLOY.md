# PAWPOSE 서버 배포 가이드 (BE-4)

서버 1대(EC2, 시드니 `ap-southeast-2`)에 Docker로 띄웁니다. 개발 서버 = 배포 서버입니다.

```
EC2 (m7i-flex.large, Ubuntu 24.04)
 ├─ nginx      :80/443 외부 공개 (80은 https로 전환) → backend로 전달
 ├─ backend    :8080 (내부)  Spring Boot, prod 프로필
 ├─ embedding  :8091 (내부)  SigLIP CPU 임베딩, 매칭 머지 후에만 켬
 └─ db         :5432 (내부)  PostgreSQL 16, 볼륨 pgdata
S3: pawpose-team28-s3-images  (EC2 역할로 접근, 키 불필요)
도메인: https://api.findpawpose.site  → 16.178.50.143 (가비아 A 레코드)
```

## 파일

| 파일 | 역할 |
|---|---|
| `Dockerfile` | Spring 빌드(JDK 17) → 실행(JRE 17) |
| `.dockerignore` | 빌드에 불필요한 파일 제외 |
| `embedding-service/Dockerfile` | Python 3.12 + CPU torch, 모델을 이미지에 미리 받음 |
| `docker-compose.yml` | db, backend, embedding, nginx |
| `deploy/nginx.conf` | 80 → https 전환, 443(SSL) → backend:8080, 업로드 50MB |
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

브라우저: `https://api.findpawpose.site/swagger-ui.html`

인증서가 아직 없으면 nginx가 시작되지 않습니다. 아래 "HTTPS 인증서" 를 먼저 진행하세요.

## HTTPS 인증서 (Let's Encrypt)

인증서와 개인키는 서버의 `/etc/letsencrypt` 에만 있고 Git에는 올리지 않습니다.

### 처음 발급 (1회)

```bash
# 가비아 DNS: A 레코드 api → 16.178.50.143 이 먼저 연결돼 있어야 함
dig +short api.findpawpose.site    # 16.178.50.143 확인

sudo mkdir -p /var/www/certbot
cd ~/Backend
docker compose stop nginx           # 80번 포트를 잠깐 비움

sudo docker run --rm -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt \
  certbot/certbot certonly --standalone \
  -d api.findpawpose.site \
  -m <이메일> --agree-tos -n

docker compose up -d nginx
```

### 갱신 (90일마다, 무중단)

```bash
sudo docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/www/certbot:/var/www/certbot \
  certbot/certbot renew --webroot -w /var/www/certbot
docker compose exec nginx nginx -s reload
```

## 자동 배포 (GitHub Actions + Docker Hub)

main 에 머지되면 `.github/workflows/deploy.yml` 이 실행됩니다.

```
main 머지
  → GitHub Actions: 이미지 빌드
  → Docker Hub push  (<계정>/pawpose-backend:latest, :<커밋SHA>)
  → EC2 SSH 접속 → deploy/deploy.sh
       git pull → docker compose pull → up -d → 오래된 이미지 정리
```

- 임베딩 이미지(`pawpose-embedding`)는 `embedding-service/server.py` 가 main 에 있을 때만 빌드됩니다.
- 진행 상황: GitHub 레포 **Actions** 탭. 수동 실행은 **Deploy → Run workflow**.

### GitHub Secrets (Settings → Secrets and variables → Actions)

| 이름 | 값 |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 계정 이름 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token (Read & Write) |
| `EC2_HOST` | `api.findpawpose.site` |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | `pawpose-key.pem` 파일 내용 전체 |

서버 `.env` 에도 `DOCKERHUB_USERNAME` 을 같은 값으로 넣어야 합니다.

### 되돌리기 (이전 버전으로)

```bash
bash ~/Backend/deploy/deploy.sh <이전 커밋 SHA>
```

## 코드 업데이트 (수동, 자동 배포가 안 될 때)

```bash
cd ~/Backend
git pull
docker compose up -d --build
```

## 매칭(F-04) 켜기 (매칭 코드는 main 에 머지됨)

```bash
# .env 에서 MATCHING_WORKER_ENABLED=true, EMBEDDING_API_KEY 확인 후
bash ~/Backend/deploy/deploy.sh          # embedding 이미지까지 받아서 같이 띄움
docker compose logs -f embedding         # 모델 로드에 20~60초
```

`deploy.sh` 는 `MATCHING_WORKER_ENABLED=true` 일 때 자동으로 `--profile matching` 을 붙입니다.
직접 `docker compose` 명령을 쓸 때는 `--profile matching` 을 붙여야 embedding 이 유지됩니다.

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
- `http://16.178.50.143` 으로 접속하면 `https://api.findpawpose.site` 로 자동 이동합니다. IP로 https 접속은 인증서가 맞지 않아 경고가 뜹니다.
- 보안 그룹(`pawpose-sg`): 22·80·443 전체 허용, 8080은 닫힘. SSH는 `.pem` 키로만 로그인되므로 키가 새지 않게 관리하고, 제출 후에는 22를 관리자 IP로 좁힙니다.
- 서버에 배포 파일을 `scp`로 먼저 복사해 둔 경우, 이 파일들이 main에 머지된 뒤 처음 `git pull` 하기 전에 복사본을 지워야 합니다.
  ```bash
  rm -rf Dockerfile .dockerignore docker-compose.yml deploy embedding-service
  git pull && docker compose up -d --build
  ```
