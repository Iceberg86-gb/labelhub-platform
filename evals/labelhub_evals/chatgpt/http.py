"""Minimal HTTP client over urllib (no extra dependency). Loopback hosts bypass the proxy."""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Iterator


class HttpError(RuntimeError):
    def __init__(self, status: int, body: bytes, url: str):
        super().__init__(f"HTTP {status} from {urllib.parse.urlsplit(url).netloc}{urllib.parse.urlsplit(url).path}")
        self.status = status
        self.body = body

    def json(self) -> dict:
        try:
            value = json.loads(self.body.decode("utf-8"))
            return value if isinstance(value, dict) else {}
        except (UnicodeDecodeError, json.JSONDecodeError):
            return {}


@dataclass
class Http:
    timeout: float = 60.0

    def _opener(self, url: str):
        host = urllib.parse.urlsplit(url).hostname or ""
        if host in ("127.0.0.1", "localhost", "::1"):
            return urllib.request.build_opener(urllib.request.ProxyHandler({}))
        return urllib.request.build_opener()

    def _request(self, method: str, url: str, headers: dict | None, form: dict | None, json_body) -> urllib.request.Request:
        data = None
        headers = dict(headers or {})
        if form is not None:
            data = urllib.parse.urlencode(form).encode("ascii")
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        elif json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        headers.setdefault("Accept", "application/json")
        return urllib.request.Request(url, data=data, headers=headers, method=method)

    def call(self, method: str, url: str, *, headers=None, form=None, json_body=None) -> bytes:
        request = self._request(method, url, headers, form, json_body)
        try:
            with self._opener(url).open(request, timeout=self.timeout) as response:
                return response.read()
        except urllib.error.HTTPError as exc:
            raise HttpError(exc.code, exc.read(), url) from None

    def json(self, method: str, url: str, **kwargs) -> dict:
        body = self.call(method, url, **kwargs)
        return json.loads(body.decode("utf-8")) if body.strip() else {}

    def stream_lines(self, method: str, url: str, *, headers=None, json_body=None) -> Iterator[str]:
        request = self._request(method, url, headers, None, json_body)
        try:
            response = self._opener(url).open(request, timeout=self.timeout)
        except urllib.error.HTTPError as exc:
            raise HttpError(exc.code, exc.read(), url) from None
        with response:
            for raw in response:
                yield raw.decode("utf-8").rstrip("\r\n")
