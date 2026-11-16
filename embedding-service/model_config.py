"""One version identifier for weights, preprocessing and stored vectors."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOCK = json.loads((ROOT / "model-lock.json").read_text(encoding="utf-8"))
MODEL_VERSION = f"{LOCK['modelId']}@{LOCK['revision']}:{LOCK['preprocessingVersion']}"
DIMENSIONS = LOCK["dimensions"]
MODEL_DIR = ROOT / ".models" / LOCK["revision"]
MAX_BYTES = 10 * 1024 * 1024
MAX_PIXELS = 16_000_000
