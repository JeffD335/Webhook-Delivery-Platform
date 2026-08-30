#!/usr/bin/env python3
"""Local HTTP receiver for webhook delivery demos.

This is course/demo infrastructure, not product code. It intentionally uses
only the Python standard library so it can run anywhere without setup.
"""

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


class ReceiverHandler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:  # noqa: N802 - required by BaseHTTPRequestHandler
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8", errors="replace")

        print("\n--- webhook received ---", flush=True)
        print(f"path: {self.path}", flush=True)
        print(f"content-type: {self.headers.get('Content-Type')}", flush=True)
        print(f"body: {body}", flush=True)

        if self.path == "/webhook":
            self._respond_empty(204)
        elif self.path == "/success":
            self._respond_json(200, {"received": True})
        elif self.path == "/fail":
            self._respond_json(500, {"error": "intentional failure"})
        elif self.path == "/slow":
            time.sleep(10)
            self._respond_json(200, {"received": True, "after": "10 seconds"})
        else:
            self._respond_json(404, {"error": "use /webhook, /success, /fail, or /slow"})

    def log_message(self, format: str, *args: object) -> None:
        return

    def _respond_empty(self, status: int) -> None:
        self.send_response(status)
        self.end_headers()

    def _respond_json(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main() -> None:
    parser = argparse.ArgumentParser(description="Local webhook demo receiver")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9090)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), ReceiverHandler)
    print(f"Receiver listening on http://{args.host}:{args.port}", flush=True)
    print("Demo endpoint URL: http://127.0.0.1:9090/webhook", flush=True)
    print("POST paths:", flush=True)
    print("  /webhook -> 204 success, empty body", flush=True)
    print("  /success -> 200 success JSON", flush=True)
    print("  /fail    -> 500 receiver failure", flush=True)
    print("  /slow    -> waits 10 seconds", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping receiver", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
