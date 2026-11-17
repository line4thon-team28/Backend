FROM python:3.12-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/tmp/hf

# CPU 전용 torch 먼저 설치 (GPU 버전보다 수 GB 작음)
RUN pip install torch==2.11.0 --index-url https://download.pytorch.org/whl/cpu

COPY requirements-lock.txt ./
RUN pip install -r requirements-lock.txt

# 실행 사용자를 먼저 만들고, 처음부터 그 사용자 소유로 복사
# (나중에 chown -R 하면 모델 파일이 레이어에 한 번 더 복사돼 이미지가 커짐)
RUN useradd -r -u 1001 embed && chown embed /app
COPY --chown=embed . .
USER embed

# 고정된 모델을 이미지 안에 미리 받아 둠
# (실행 시에는 인터넷 없이 로컬 파일만 로드하고 SHA-256 검증)
# 이미지에 모델 가중치가 포함되므로 licenses/ 와 THIRD_PARTY_NOTICES.md 도 함께 들어가야 함
RUN python download_model.py

ENV EMBEDDING_BIND=0.0.0.0 \
    EMBEDDING_PORT=8091 \
    EMBEDDING_CPU_THREADS=2
EXPOSE 8091

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8091/health', timeout=4).status == 200 else 1)"

CMD ["python", "server.py"]
