import http.client
import io
import json
import math
import sys
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PIL import Image
from embedding import InvalidImage, decode_image, normalize
from model_config import DIMENSIONS, MAX_BYTES, MODEL_VERSION
from server import EmbeddingServer

KEY = "test-only-private-key-" + "x" * 32


def picture(format="PNG", size=(24, 24)):
    stream = io.BytesIO()
    Image.new("RGB", size, (100, 80, 30)).save(stream, format=format)
    return stream.getvalue()


class FakeEmbedder:
    def embed(self, raw):
        decode_image(raw)
        return normalize([1.0] * DIMENSIONS)


class ImageTests(unittest.TestCase):
    def test_jpeg_png_decode_to_rgb(self):
        for format in ("JPEG", "PNG"):
            self.assertEqual("RGB", decode_image(picture(format)).mode)

    def test_unrecognized_and_unsupported_formats_rejected(self):
        for raw in (b"not an image", picture("GIF"), b""):
            with self.assertRaises(InvalidImage):
                decode_image(raw)

    def test_decoded_pixel_limit(self):
        with patch("embedding.MAX_PIXELS", 10):
            with self.assertRaises(InvalidImage):
                decode_image(picture())

    def test_unit_vector_and_invalid_model_outputs(self):
        result = normalize([1] * DIMENSIONS)
        self.assertAlmostEqual(1, math.sqrt(sum(x*x for x in result)))
        for values in ([1], [0] * DIMENSIONS, [float("nan")] * DIMENSIONS):
            with self.assertRaises(ValueError):
                normalize(values)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.server = EmbeddingServer(("127.0.0.1", 0), FakeEmbedder(), KEY)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def request(self, method="POST", path="/embed", body=None, headers=None):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        actual = {"Content-Type": "application/octet-stream", "X-Embedding-Key": KEY}
        actual.update(headers or {})
        try:
            connection.request(method, path, body, actual)
            response = connection.getresponse()
            return response.status, json.loads(response.read())
        finally:
            connection.close()

    def test_authenticated_binary_contract(self):
        status, data = self.request(body=picture())
        self.assertEqual(200, status)
        self.assertEqual(MODEL_VERSION, data["modelVersion"])
        self.assertEqual(DIMENSIONS, len(data["vector"]))

    def test_invalid_key_cannot_infer(self):
        self.assertEqual(401, self.request(body=picture(), headers={"X-Embedding-Key": "wrong"})[0])

    def test_length_limit_before_reading_body(self):
        self.assertEqual(413, self.request(body=b"", headers={"Content-Length": str(MAX_BYTES+1)})[0])

    def test_invalid_image_and_content_type(self):
        self.assertEqual(422, self.request(body=b"not an image")[0])
        self.assertEqual(415, self.request(body=picture(), headers={"Content-Type": "text/plain"})[0])

    def test_busy_is_rejected_without_queueing(self):
        self.server.embedding_slot.acquire()
        try:
            self.assertEqual(503, self.request(body=picture())[0])
        finally:
            self.server.embedding_slot.release()

    def test_health_does_not_expose_key(self):
        status, data = self.request(method="GET", path="/health")
        self.assertEqual(200, status)
        self.assertTrue(data["ready"])
        self.assertNotIn(KEY, json.dumps(data))

    def test_weak_key_fails_at_startup(self):
        with self.assertRaises(ValueError):
            EmbeddingServer(("127.0.0.1", 0), FakeEmbedder(), "short")


if __name__ == "__main__":
    unittest.main()
