"""Explicit, free download of pinned weights; never invoked by the HTTP service."""
import hashlib
import json
from pathlib import Path

from huggingface_hub import snapshot_download

from model_config import LOCK, MODEL_DIR, MODEL_VERSION

FILES = ("config.json", "preprocessor_config.json", "model.safetensors", "README.md")


def main():
    snapshot_download(
        repo_id=LOCK["modelId"], revision=LOCK["revision"],
        local_dir=str(MODEL_DIR), allow_patterns=list(FILES),
    )
    checksums = {}
    for name in FILES:
        with (MODEL_DIR / name).open("rb") as stream:
            checksums[name] = hashlib.file_digest(stream, "sha256").hexdigest()
    manifest = {"modelVersion": MODEL_VERSION, "sha256": checksums}
    (MODEL_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {MODEL_VERSION} in {MODEL_DIR}")


if __name__ == "__main__":
    main()

