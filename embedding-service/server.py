"""Private, bounded HTTP adapter. Spring owns jobs, retries and image downloads."""
import hmac
import json
import logging
import os
import socket
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn

from embedding import InvalidImage, SiglipEmbedder
from model_config import DIMENSIONS, MAX_BYTES, MODEL_VERSION

LOG = logging.getLogger("embedding")


class EmbeddingServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    request_queue_size = 8

    def __init__(self, address, embedder, api_key):
        if not api_key or len(api_key.encode("utf-8")) < 32:
            raise ValueError("EMBEDDING_API_KEY must contain at least 32 bytes")
        self.embedder = embedder
        self.api_key = api_key.encode("utf-8")
        self.embedding_slot = threading.BoundedSemaphore(1)
        self.connection_slots = threading.BoundedSemaphore(8)
        super().__init__(address, EmbeddingHandler)

    def get_request(self):
        client, address = super().get_request()
        client.settimeout(15)
        return client, address

    def process_request(self, request, client_address):
        if not self.connection_slots.acquire(blocking=False):
            try:
                request.sendall(b"HTTP/1.0 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            except OSError:
                pass
            finally:
                self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.connection_slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.connection_slots.release()


class EmbeddingHandler(BaseHTTPRequestHandler):
    server_version = "PawposeEmbedding"

    def log_message(self, format, *args):
        # Do not log headers, request bodies or paths supplied by callers.
        pass

    def reply(self, status, payload):
        body = json.dumps(payload, allow_nan=False, separators=(",", ":")).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            if status == 503:
                self.send_header("Retry-After", "2")
            self.end_headers()
            self.wfile.write(body)
            self.wfile.flush()
            # Send FIN after the response before closing a socket whose rejected
            # request body may remain unread (otherwise Windows can send RST).
            self.connection.shutdown(socket.SHUT_WR)
        except (BrokenPipeError, ConnectionResetError, socket.timeout):
            pass
        self.close_connection = True

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, {"ready": True, "modelVersion": MODEL_VERSION, "dimensions": DIMENSIONS})
        else:
            self.reply(404, {"code": "NOT_FOUND"})

    def do_POST(self):
        if self.path != "/embed":
            return self.reply(404, {"code": "NOT_FOUND"})
        supplied = self.headers.get("X-Embedding-Key", "").encode("utf-8")
        if not hmac.compare_digest(supplied, self.server.api_key):
            return self.reply(401, {"code": "UNAUTHORIZED"})
        if self.headers.get_content_type() != "application/octet-stream":
            return self.reply(415, {"code": "UNSUPPORTED_MEDIA_TYPE"})
        if self.headers.get("Transfer-Encoding") or len(self.headers.get_all("Content-Length", [])) != 1:
            return self.reply(400, {"code": "INVALID_LENGTH"})
        try:
            length = int(self.headers["Content-Length"])
        except (ValueError, TypeError):
            return self.reply(400, {"code": "INVALID_LENGTH"})
        if length <= 0 or length > MAX_BYTES:
            return self.reply(413, {"code": "IMAGE_TOO_LARGE"})
        if not self.server.embedding_slot.acquire(blocking=False):
            return self.reply(503, {"code": "BUSY"})
        try:
            raw = self.rfile.read(length)
            if len(raw) != length:
                return self.reply(400, {"code": "INCOMPLETE_BODY"})
            vector = self.server.embedder.embed(raw)
            self.reply(200, {"modelVersion": MODEL_VERSION, "dimensions": DIMENSIONS, "vector": vector})
        except InvalidImage:
            self.reply(422, {"code": "INVALID_IMAGE"})
        except socket.timeout:
            self.reply(408, {"code": "READ_TIMEOUT"})
        except Exception:
            LOG.error("Image inference failed")
            self.reply(500, {"code": "INFERENCE_FAILED"})
        finally:
            self.server.embedding_slot.release()


def main():
    key = os.environ.get("EMBEDDING_API_KEY", "")
    if len(key.encode("utf-8")) < 32:
        raise SystemExit("Set EMBEDDING_API_KEY to a private value of at least 32 bytes")
    threads = int(os.environ.get("EMBEDDING_CPU_THREADS", "2"))
    if not 1 <= threads <= 8:
        raise SystemExit("EMBEDDING_CPU_THREADS must be between 1 and 8")
    embedder = SiglipEmbedder(threads=threads)
    address = (os.environ.get("EMBEDDING_BIND", "127.0.0.1"), int(os.environ.get("EMBEDDING_PORT", "8091")))
    server = EmbeddingServer(address, embedder, key)
    LOG.info("CPU embedding service ready: %s", MODEL_VERSION)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    main()
