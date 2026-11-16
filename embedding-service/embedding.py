"""Decode untrusted image bytes, then compute normalized CPU image features."""
import hashlib
import io
import json
import math
import warnings

from PIL import Image, ImageOps, UnidentifiedImageError

from model_config import DIMENSIONS, MAX_BYTES, MAX_PIXELS, MODEL_DIR, MODEL_VERSION

Image.MAX_IMAGE_PIXELS = MAX_PIXELS


class InvalidImage(ValueError):
    pass


def decode_image(raw: bytes):
    if not raw or len(raw) > MAX_BYTES:
        raise InvalidImage("Image must contain 1 byte to 10 MiB")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(raw)) as image:
                if image.format not in {"JPEG", "PNG"}:
                    raise InvalidImage("Only JPEG and PNG are supported")
                if image.width * image.height > MAX_PIXELS or getattr(image, "n_frames", 1) != 1:
                    raise InvalidImage("Image dimensions or frame count exceed the limit")
                image.load()
                return ImageOps.exif_transpose(image).convert("RGB")
    except (UnidentifiedImageError, OSError, SyntaxError, Image.DecompressionBombError,
            Image.DecompressionBombWarning) as exc:
        raise InvalidImage("Image cannot be decoded") from exc


def normalize(vector):
    values = [float(value) for value in vector]
    if len(values) != DIMENSIONS or not all(math.isfinite(value) for value in values):
        raise ValueError("Invalid model output")
    norm = math.sqrt(sum(value * value for value in values))
    if norm < 1e-12:
        raise ValueError("Zero model output")
    return [value / norm for value in values]


class SiglipEmbedder:
    def __init__(self, model_dir=MODEL_DIR, threads=2):
        # Startup is offline. A missing/corrupt download is a startup failure,
        # not a hidden download or an attempt to use managed inference.
        manifest = json.loads((model_dir / "manifest.json").read_text(encoding="utf-8"))
        if manifest.get("modelVersion") != MODEL_VERSION:
            raise ValueError("Model manifest version does not match model-lock.json")
        for name in ("config.json", "preprocessor_config.json", "model.safetensors"):
            with (model_dir / name).open("rb") as stream:
                actual = hashlib.file_digest(stream, "sha256").hexdigest()
            if actual != manifest.get("sha256", {}).get(name):
                raise ValueError("Model file checksum mismatch")

        import torch
        from transformers import SiglipImageProcessor, SiglipVisionModel

        torch.set_num_threads(threads)
        self.torch = torch
        self.processor = SiglipImageProcessor.from_pretrained(str(model_dir), local_files_only=True)
        self.model = SiglipVisionModel.from_pretrained(
            str(model_dir), local_files_only=True, use_safetensors=True,
        ).to("cpu").eval()

    def embed(self, raw):
        image = decode_image(raw)
        inputs = self.processor(images=image, return_tensors="pt")
        with self.torch.inference_mode():
            vector = self.model(**inputs).pooler_output[0].tolist()
        return normalize(vector)
