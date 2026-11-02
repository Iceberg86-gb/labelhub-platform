"""OAuth 2.0 authorization-code + PKCE flow for Sign in with ChatGPT (ADR-018 §2).

Order matters: the loopback listener is started *before* the browser opens, and the exact same
redirect_uri is used for the authorize request and the code exchange. Only the port may vary.
"""

from __future__ import annotations

import base64
import hashlib
import http.server
import json
import secrets
import socket
import threading
import time
import urllib.parse
import uuid
from dataclasses import dataclass
from pathlib import Path

from labelhub_evals import CLIENT_NAME
from labelhub_evals.chatgpt.config import Endpoints
from labelhub_evals.chatgpt.credentials import Credentials, _atomic_write
from labelhub_evals.chatgpt.http import Http, HttpError

DYNAMIC_CLIENT_ID = "dynamic_agent_client"
SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
REQUIRED_SCOPE = "chatgpt.tokens.use.direct"
DEFAULT_PORT = 1455
CALLBACK_PATH = "/auth/callback"
LOOPBACK_HOST = "127.0.0.1"  # the docs require 127.0.0.1, never "localhost"


class OAuthError(RuntimeError):
    pass


# --- PKCE / state / nonce ------------------------------------------------------------------------
def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


@dataclass(frozen=True)
class AuthAttempt:
    verifier: str
    challenge: str
    state: str
    nonce: str

    @staticmethod
    def new() -> "AuthAttempt":
        verifier = _b64url(secrets.token_bytes(64))
        challenge = _b64url(hashlib.sha256(verifier.encode("ascii")).digest())
        return AuthAttempt(verifier, challenge, _b64url(secrets.token_bytes(32)), _b64url(secrets.token_bytes(32)))


# --- client registration (non-secret, persisted) -----------------------------------------------
@dataclass
class Registration:
    host_id: str
    client_id: str | None = None

    @staticmethod
    def load(home: Path) -> "Registration":
        path = home / "client.json"
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
            return Registration(data["host_id"], data.get("client_id"))
        registration = Registration(f"urn:uuid:{uuid.uuid4()}")
        registration.save(home)
        return registration

    def save(self, home: Path) -> None:
        _atomic_write(home / "client.json", json.dumps({"client_id": self.client_id, "host_id": self.host_id}).encode("utf-8"))


def authorize_url(endpoints: Endpoints, registration: Registration, attempt: AuthAttempt, redirect_uri: str, *, id_token_hint: str | None = None, login_hint: str | None = None) -> str:
    first = registration.client_id is None
    params = {
        "client_id": DYNAMIC_CLIENT_ID if first else registration.client_id,
        "ext_agent_host_id": registration.host_id,
        "response_type": "code",
        "redirect_uri": redirect_uri,
        "scope": SCOPE,
        "resource": endpoints.resource,
        "state": attempt.state,
        "nonce": attempt.nonce,
        "code_challenge_method": "S256",
        "code_challenge": attempt.challenge,
    }
    if first:
        params["agent_name_hint"] = CLIENT_NAME
    if id_token_hint:
        params["id_token_hint"] = id_token_hint
    if login_hint:
        params["login_hint"] = login_hint
    return endpoints.authorize + "?" + urllib.parse.urlencode(params, quote_via=urllib.parse.quote)


def redact_url(url: str) -> str:
    """For logs/terminal: never print id_token_hint (docs: redact it from logged authorize URLs)."""
    parts = urllib.parse.urlsplit(url)
    query = [(k, "REDACTED" if k == "id_token_hint" else v) for k, v in urllib.parse.parse_qsl(parts.query)]
    return urllib.parse.urlunsplit(parts._replace(query=urllib.parse.urlencode(query, quote_via=urllib.parse.quote)))


# --- loopback callback server -------------------------------------------------------------------
@dataclass
class CallbackResult:
    params: dict

    def get(self, key: str) -> str | None:
        return self.params.get(key)


_PAGE = """<!doctype html><meta charset="utf-8"><title>LabelHub Evals</title>
<body style="font-family:system-ui;margin:3rem;max-width:32rem"><h2>{title}</h2><p>{body}</p></body>"""


class CallbackServer:
    def __init__(self, port: int = DEFAULT_PORT):
        self.result: CallbackResult | None = None
        self._done = threading.Event()
        owner = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):  # noqa: N802
                parts = urllib.parse.urlsplit(self.path)
                if parts.path != CALLBACK_PATH:
                    self.send_response(404)
                    self.end_headers()
                    return
                params = dict(urllib.parse.parse_qsl(parts.query))
                ok = "code" in params and "error" not in params
                page = _PAGE.format(
                    title="Signed in" if ok else "Sign-in did not complete",
                    body="You can close this tab and return to the terminal." if ok else "Return to the terminal for details.",
                )
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(page.encode("utf-8"))
                if owner.result is None:
                    owner.result = CallbackResult(params)
                    owner._done.set()

            def log_message(self, *args):  # keep codes out of terminal logs
                pass

        try:
            self._server = http.server.HTTPServer((LOOPBACK_HOST, port), Handler)
        except OSError:
            # Port busy: the docs allow only the port to vary, so take any free one.
            self._server = http.server.HTTPServer((LOOPBACK_HOST, 0), Handler)
        self.port = self._server.server_address[1]
        self.redirect_uri = f"http://{LOOPBACK_HOST}:{self.port}{CALLBACK_PATH}"
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)

    def __enter__(self) -> "CallbackServer":
        self._thread.start()
        return self

    def __exit__(self, *exc) -> None:
        self._server.shutdown()
        self._server.server_close()

    def wait(self, timeout: float) -> CallbackResult:
        if not self._done.wait(timeout):
            raise OAuthError(f"no browser callback within {int(timeout)}s")
        return self.result  # type: ignore[return-value]


def port_is_free(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        try:
            sock.bind((LOOPBACK_HOST, port))
            return True
        except OSError:
            return False


def validate_callback(result: CallbackResult, attempt: AuthAttempt, registration: Registration) -> tuple[str, str]:
    """Returns (code, client_id). Raises OAuthError on any mismatch."""
    error = result.get("error")
    if error == "access_denied":
        raise OAuthError("sign-in was cancelled in the browser (access_denied)")
    if error:
        raise OAuthError(f"authorization failed: {error}")
    if not secrets.compare_digest(result.get("state") or "", attempt.state):
        raise OAuthError("state mismatch on callback; possible CSRF, aborting")
    code = result.get("code")
    if not code:
        raise OAuthError("callback had no authorization code")
    returned_client = result.get("client_id")
    if registration.client_id is None:
        if not returned_client:
            raise OAuthError("first registration callback did not return an issued client_id")
        if returned_client == DYNAMIC_CLIENT_ID:
            raise OAuthError("callback returned the placeholder client_id instead of an issued one")
        return code, returned_client
    if returned_client and returned_client != registration.client_id:
        raise OAuthError("callback client_id differs from the registered client; rejecting")
    return code, registration.client_id


# --- tokens ---------------------------------------------------------------------------------------
def decode_jwt_claims(token: str | None) -> dict:
    """Read claims without verifying the signature. Used only for nonce/expiry/identity hints from
    a token we just received over TLS from the token endpoint, never as an authorization decision."""
    if not token or token.count(".") < 2:
        return {}
    payload = token.split(".")[1]
    try:
        return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    except (ValueError, json.JSONDecodeError):
        return {}


def account_identity(id_claims: dict) -> tuple[str, str | None]:
    """(account_key, email). Prefer a workspace/account identifier; email and sub alone are not
    workspace identifiers, so they are only a last-resort key, prefixed to make that visible."""
    auth = id_claims.get("https://api.openai.com/auth") or {}
    for key in ("chatgpt_account_id", "workspace_id", "account_id"):
        value = auth.get(key) if isinstance(auth, dict) else None
        if value:
            return f"account:{value}", id_claims.get("email")
    for key in ("workspace_id", "account_id"):
        if id_claims.get(key):
            return f"account:{id_claims[key]}", id_claims.get("email")
    return f"sub:{id_claims.get('sub', 'unknown')}", id_claims.get("email")


def granted_scopes(token_response: dict, access_token: str) -> list[str]:
    scope = token_response.get("scope")
    if isinstance(scope, str):
        return scope.split()
    claims = decode_jwt_claims(access_token)
    for key in ("scp", "scope"):
        value = claims.get(key)
        if isinstance(value, list):
            return [str(v) for v in value]
        if isinstance(value, str):
            return value.split()
    return []


def _credentials_from(response: dict, client_id: str, *, now: float, previous: Credentials | None = None, nonce: str | None = None) -> Credentials:
    access = response.get("access_token")
    if not access:
        raise OAuthError("token response has no access_token")
    refresh = response.get("refresh_token") or (previous.refresh_token if previous else None)
    if not refresh:
        raise OAuthError("token response has no refresh_token (offline_access not granted?)")
    id_token = response.get("id_token") or (previous.id_token if previous else None)
    claims = decode_jwt_claims(id_token)
    if nonce is not None and claims.get("nonce") not in (None, nonce):
        raise OAuthError("id_token nonce mismatch; aborting")
    scopes = granted_scopes(response, access) or (previous.scopes if previous else [])
    if REQUIRED_SCOPE not in scopes:
        raise OAuthError(f"granted scopes do not include {REQUIRED_SCOPE}; this account cannot use ChatGPT plan inference here")
    account_key, email = account_identity(claims)
    if previous is not None and claims == {}:
        account_key, email = previous.account_key, previous.email
    expires_in = int(response.get("expires_in") or 3600)
    return Credentials(client_id, account_key, email, access, refresh, id_token, int(now) + expires_in, scopes)


def exchange_code(http: Http, endpoints: Endpoints, *, client_id: str, code: str, attempt: AuthAttempt, redirect_uri: str, now: float | None = None) -> Credentials:
    try:
        response = http.json("POST", endpoints.token, form={
            "grant_type": "authorization_code",
            "client_id": client_id,
            "code": code,
            "code_verifier": attempt.verifier,
            "redirect_uri": redirect_uri,
            "resource": endpoints.resource,
        })
    except HttpError as exc:
        raise OAuthError(f"code exchange failed: {exc} {exc.json().get('error', '')}".strip()) from None
    return _credentials_from(response, client_id, now=time.time() if now is None else now, nonce=attempt.nonce)


def refresh(http: Http, endpoints: Endpoints, previous: Credentials, *, now: float | None = None) -> Credentials:
    try:
        response = http.json("POST", endpoints.token, form={
            "grant_type": "refresh_token",
            "client_id": previous.client_id,
            "refresh_token": previous.refresh_token,
            "resource": endpoints.resource,
            # scope omitted on purpose: keeps the original grant
        })
    except HttpError as exc:
        error = exc.json().get("error", "")
        raise OAuthError(f"token refresh failed ({exc.status} {error}); run `chatgpt login` again".strip()) from None
    return _credentials_from(response, previous.client_id, now=time.time() if now is None else now, previous=previous)


def revoke(http: Http, endpoints: Endpoints, credentials: Credentials) -> bool:
    try:
        endpoint = http.json("GET", endpoints.openid_configuration).get("revocation_endpoint")
        if not endpoint:
            return False
        http.call("POST", endpoint, form={"token": credentials.refresh_token, "token_type_hint": "refresh_token", "client_id": credentials.client_id})
        return True
    except (HttpError, OSError, ValueError):
        return False
