"""Opt-in real-model smoke test using original synthetic images, not animal accuracy."""
import io
import json
import math
import time

from PIL import Image, ImageDraw

from embedding import SiglipEmbedder
from model_config import DIMENSIONS, MODEL_VERSION


def main():
    start = time.perf_counter()
    embedder = SiglipEmbedder()
    load_seconds = time.perf_counter() - start
    image = Image.new("RGB", (320, 240), "white")
    ImageDraw.Draw(image).ellipse((60, 20, 250, 200), fill="brown")
    raw = io.BytesIO()
    image.save(raw, format="PNG")
    timings, vectors = [], []
    for _ in range(2):
        start = time.perf_counter()
        vectors.append(embedder.embed(raw.getvalue()))
        timings.append(time.perf_counter() - start)
    assert all(len(vector) == DIMENSIONS for vector in vectors)
    assert all(all(math.isfinite(x) for x in vector) for vector in vectors)
    cosine = sum(a*b for a,b in zip(*vectors))
    assert cosine > 0.99999
    print(json.dumps({"modelVersion": MODEL_VERSION, "dimensions": DIMENSIONS,
                      "loadSeconds": round(load_seconds, 3),
                      "inferenceSeconds": [round(t, 3) for t in timings],
                      "sameImageCosine": cosine,
                      "scope": "Synthetic image CPU smoke only; no animal retrieval accuracy evaluation"}, indent=2))


if __name__ == "__main__":
    main()
