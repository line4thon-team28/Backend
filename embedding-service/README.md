# CPU 이미지 임베딩

Spring이 전달한 사진 한 장을 벡터로 바꿉니다. 공고 수집, 사진 URL 다운로드, 작업 재시도, 후보 점수·알림은 Spring이 담당합니다. 유료 추론 API를 호출하지 않습니다.

## 설치와 실행

Python 3.12 기준입니다. 아래 명령은 이 디렉터리에서 실행합니다. Windows는 `.venv/bin/python` 대신 `.venv\Scripts\python.exe`를 사용합니다.

```sh
python -m venv .venv
.venv/bin/python -m pip install torch==2.11.0 --index-url https://download.pytorch.org/whl/cpu
.venv/bin/python -m pip install -r requirements-lock.txt
.venv/bin/python download_model.py
```

마지막 명령만 모델을 다운로드합니다. 공개 모델 파일을 받는 것이며, 호스팅된 추론 서비스를 이용하지 않습니다. CPU 실행에 사용할 RAM은 여유 있게 확보해야 합니다. 가중치와 가상환경은 Git에 포함되지 않습니다.

실행 전 `EMBEDDING_API_KEY`에 32바이트 이상의 무작위 비밀값을 설정하고 Spring 쪽에도 같은 값을 설정합니다. 값은 Git에 올리지 않습니다.

```sh
.venv/bin/python server.py
```

기본 주소는 `127.0.0.1:8091`입니다. `EMBEDDING_BIND`, `EMBEDDING_PORT`로 변경할 수 있으나 외부에 공개하지 않습니다. 서로 다른 컨테이너를 쓸 경우 내부 네트워크에서만 연결합니다. `EMBEDDING_CPU_THREADS`는 기본 2, 범위 1~8입니다.

실행 시 모델 파일의 SHA-256과 버전을 확인한 뒤 로컬 파일만 로드합니다. 다운로드가 빠졌거나 파일이 달라지면 시작하지 않습니다. 자동으로 다른 모델을 받거나 유료 API로 대체하지 않습니다.

## Spring 호출 규격

`POST /embed`

- `Content-Type: application/octet-stream`
- `X-Embedding-Key: <private shared key>`
- 본문: 사진의 원본 바이트. URL이나 base64 JSON을 받지 않습니다.
- 최대 10 MiB, 16백만 픽셀, 단일 프레임 JPEG/PNG
- HEIC는 업로드 담당 모듈에서 JPEG/PNG로 변환한 뒤 전달합니다.

```json
{
  "modelVersion": "google/siglip-base-patch16-224@7fd15f0689c79d79e38b1c2e2e2370a7bf2761ed:rgb224-v1",
  "dimensions": 768,
  "vector": [0.01, -0.02]
}
```

위 벡터는 형식 예시로 줄였습니다. 실제 응답에는 유한한 실수 768개가 있고 L2 norm은 1입니다. 동일한 `modelVersion`끼리만 비교해야 합니다. EXIF 방향 보정 → RGB 변환 → 고정 모델의 공식 이미지 전처리(224×224) → vision pooling → L2 정규화를 사용합니다.

| 상태 | 의미 | Spring 처리 |
|---|---|---|
| 200 | 벡터 생성 완료 | 차원·버전 검증 후 저장 |
| 400 / 413 / 415 / 422 | 요청 형식, 크기, 이미지 문제 | 같은 파일을 계속 재시도하지 않고 실패 상태 표시 |
| 401 | 내부 인증키 불일치 | 설정 확인 |
| 408 | 사진 본문 수신 시간 초과 | 제한적으로 재시도 |
| 503 | 다른 추론 작업 실행 중 | 지연 후 재시도 |
| 500 | 추론 실패 | 제한적으로 재시도 후 실패 표시 |

추론은 한 번에 1건입니다. 추가 요청을 추론 대기열에 쌓지 않고 503으로 돌려줍니다. HTTP 연결도 최대 8개, 읽기 제한시간은 15초입니다. Spring에서 별도 요청 제한시간을 설정해야 합니다.

`GET /health`는 모델 로드가 끝난 후 `ready`, `modelVersion`, `dimensions`를 반환합니다. API 키와 사진 본문은 로그에 남기지 않습니다.

## 검증

```sh
.venv/bin/python -m unittest discover -s tests -v
.venv/bin/python smoke_model.py
```

단위·HTTP 테스트는 가짜 벡터 생성기를 사용하므로 모델 다운로드가 필요 없습니다. `smoke_model.py`는 실제 모델과 직접 생성한 도형 이미지를 사용합니다. 가중치 로드·CPU 추론·출력 차원·동일 이미지 일관성만 확인하며, 동물 개체 식별 정확도를 측정한 결과가 아닙니다.

2026-11-15 Windows / Python 3.12 / torch 2.11.0 CPU / 2 threads에서 HTTP·이미지 테스트 11건이 통과했습니다. 실제 모델은 오프라인 실행으로 로드 23.104초, 동일 이미지 두 번의 추론은 1.374초·1.111초였습니다. 다른 서버의 속도 보장은 아니며 Spring 다운로드·대기·DB 시간을 포함하지 않습니다.

## 모델과 점수의 한계

SigLIP은 일반 이미지 표현 모델이며 같은 반려동물인지 판정하도록 학습된 모델은 아닙니다. 코사인 유사도는 동일 개체일 확률이 아닙니다. 후보 임계값과 가중치는 권한이 확인된 실제 동물 사진으로 별도 평가해야 합니다.

모델·전처리를 바꾸면 버전도 변경하고 기존 사진을 다시 임베딩합니다. `model-lock.json`과 [라이선스 고지](THIRD_PARTY_NOTICES.md)를 함께 확인합니다.

