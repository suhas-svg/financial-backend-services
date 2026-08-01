#!/usr/bin/env python3
import hashlib
import json
import os
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

EVIDENCE = Path(os.environ.get("ALERT_EVIDENCE_DIRECTORY", "/evidence"))
EVIDENCE.mkdir(parents=True, exist_ok=True)

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            self.send_error(404)
            return
        self.send_response(200); self.end_headers(); self.wfile.write(b"ok")

    def do_POST(self):
        if self.path != "/alerts":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(min(length, 1024 * 1024))
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400); return
        record = {
            "receivedAt": datetime.now(timezone.utc).isoformat(),
            "status": payload.get("status"),
            "groupKey": payload.get("groupKey"),
            "alerts": [{"status": a.get("status"), "labels": a.get("labels", {}),
                        "annotations": a.get("annotations", {})} for a in payload.get("alerts", [])],
            "payloadSha256": hashlib.sha256(body).hexdigest(),
        }
        with (EVIDENCE / "alert-receipts.ndjson").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(record, sort_keys=True) + "\n")
        self.send_response(204); self.end_headers()

    def log_message(self, format, *args):
        return

ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
