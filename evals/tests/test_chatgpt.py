"""Contracts of Sign in with ChatGPT (ADR-018), exercised against a local stub of auth + API endpoints."""

from __future__ import annotations

import base64
import http.server
import json
import os
import stat
import threading
import urllib.parse
import urllib.request

import pytest

from labelhub_evals import review
from labelhub_evals.chatgpt import oauth
from labelhub_evals.chatgpt.config import Endpoints
from labelhub_evals.chatgpt.credentials import Credentials, CredentialsStore
from labelhub_evals.chatgpt.http import Http, HttpError
from labelhub_evals.chatgpt.models import ModelInfo, parse_catalog, pick_latest, ranked
from labelhub_evals.chatgpt.profile import ProfileError, ProfileStore, Selection
from labelhub_evals.chatgpt.responses import ChatGPTProvider, ResponsesClient, consume, iter_sse
from labelhub_evals.chatgpt.session import ChatGPTSession
from labelhub_evals.providers.base import ProviderError

NOW = 1_800_000_000


def jwt(claims: dict) -> str:
    enc = lambda d: base64.urlsafe_b64encode(json.dumps(d).encode()).rstrip(b"=").decode()  # noqa: E731
    return f"{enc({'alg': 'none'})}.{enc(claims)}.sig"


ID_CLAIMS = {"sub": "user-1", "email": "owner@example.com", "https://api.openai.com/auth": {"chatgpt_account_id": "ws-1"}}


class Stub:
    """Configurable fake of auth.openai.com + api.openai.com/v1."""

    def __init__(self):
        self.token_requests: list[dict] = []
        self.responses_auth: list[str] = []
        self.revoked: list[dict] = []
        self.nonce = None
        self.scope = oauth.SCOPE
        self.unauthorized_remaining = 0
        self.token_counter = 0
        self.catalog = {"data": [
            {"slug": "gpt-5.4", "display_name": "GPT-5.4", "visibility": "list", "priority": 1, "supported_reasoning_levels": [{"effort": "low"}, {"effort": "medium"}], "default_reasoning_level": "medium"},
            {"slug": "gpt-5.5", "display_name": "GPT-5.5", "visibility": "list", "priority": 1, "supported_reasoning_levels": ["low", "medium", "high"]},
            {"slug": "gpt-5.5-mini", "visibility": "list", "priority": 3},
            {"slug": "internal-x", "visibility": "hide", "priority": 0},
        ]}
        self.sse_events = None
        stub = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def _send(self, status, body, ctype="application/json"):
                data = body if isinstance(body, bytes) else json.dumps(body).encode()
                self.send_response(status)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_GET(self):  # noqa: N802
                if self.path == "/.well-known/openid-configuration":
                    return self._send(200, {"revocation_endpoint": f"{stub.base}/oauth/revoke"})
                if self.path.startswith("/v1/models"):
                    return self._send(200, stub.catalog)
                self._send(404, {})

            def do_POST(self):  # noqa: N802
                raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
                if self.path == "/api/accounts/oauth/token":
                    form = dict(urllib.parse.parse_qsl(raw.decode()))
                    stub.token_requests.append(form)
                    if form.get("refresh_token") == "dead":
                        return self._send(400, {"error": "invalid_grant"})
                    stub.token_counter += 1
                    n = stub.token_counter
                    return self._send(200, {
                        "access_token": f"access-{n}", "refresh_token": f"refresh-{n}", "expires_in": 3600, "scope": stub.scope,
                        "id_token": jwt(dict(ID_CLAIMS, nonce=stub.nonce)) if form["grant_type"] == "authorization_code" else None,
                    })
                if self.path == "/oauth/revoke":
                    stub.revoked.append(dict(urllib.parse.parse_qsl(raw.decode())))
                    return self._send(200, b"", "text/plain")
                if self.path == "/v1/responses":
                    stub.responses_auth.append(self.headers.get("Authorization"))
                    body = json.loads(raw)
                    assert body["store"] is False and body["stream"] is True
                    if stub.unauthorized_remaining > 0:
                        stub.unauthorized_remaining -= 1
                        return self._send(401, {"error": {"code": "invalid_token"}})
                    events = stub.sse_events or default_events()
                    payload = "".join(f"event: {e['type']}\ndata: {json.dumps(e)}\n\n" for e in events).encode()
                    return self._send(200, payload, "text/event-stream")
                self._send(404, {})

            def log_message(self, *args):
                pass

        self.server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    @property
    def endpoints(self) -> Endpoints:
        return Endpoints(self.base, self.base + "/v1")


def default_events():
    args = json.dumps({"overallSuggestion": "pass", "confidence": 0.9, "summary": "ok", "fieldFindings": [], "dimensionScores": [{"dimension": "overall", "score": 0.9}]})
    return [
        {"type": "response.created"},
        {"type": "response.output_text.delta", "delta": "Hel"},
        {"type": "response.output_text.delta", "delta": "lo"},
        {"type": "response.output_item.done", "item": {"type": "function_call", "name": review.TOOL_NAME, "arguments": args, "call_id": "c1"}},
        {"type": "response.completed", "response": {"usage": {"input_tokens": 120, "output_tokens": 30}}},
    ]


@pytest.fixture
def stub():
    s = Stub()
    yield s
    s.server.shutdown()


@pytest.fixture
def store(tmp_path):
    return CredentialsStore(tmp_path, use_keyring=False)


def creds(expires_at=NOW + 3600, refresh="refresh-0") -> Credentials:
    return Credentials("oaiapp_1", "account:ws-1", "owner@example.com", "access-0", refresh, jwt(ID_CLAIMS), expires_at, oauth.SCOPE.split())


# --- authorize request ------------------------------------------------------------------------------
def test_first_authorize_uses_dynamic_client_registration_pkce_s256_and_127_0_0_1(tmp_path):
    attempt = oauth.AuthAttempt.new()
    reg = oauth.Registration.load(tmp_path)
    url = oauth.authorize_url(Endpoints(), reg, attempt, "http://127.0.0.1:1455/auth/callback")
    q = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))
    assert url.startswith("https://auth.openai.com/api/accounts/authorize?")
    assert q["client_id"] == "dynamic_agent_client" and q["agent_name_hint"] == "LabelHub Evals"
    assert q["ext_agent_host_id"].startswith("urn:uuid:") and q["resource"] == "https://api.openai.com/v1"
    assert q["redirect_uri"] == "http://127.0.0.1:1455/auth/callback"
    assert "chatgpt.tokens.use.direct" in q["scope"].split() and "offline_access" in q["scope"].split()
    expected = base64.urlsafe_b64encode(__import__("hashlib").sha256(attempt.verifier.encode()).digest()).rstrip(b"=").decode()
    assert q["code_challenge_method"] == "S256" and q["code_challenge"] == expected
    assert q["state"] == attempt.state and q["nonce"] == attempt.nonce


def test_reauthorization_uses_issued_client_without_name_hint_and_redacts_id_token_hint(tmp_path):
    reg = oauth.Registration.load(tmp_path)
    reg.client_id = "oaiapp_1"
    url = oauth.authorize_url(Endpoints(), reg, oauth.AuthAttempt.new(), "http://127.0.0.1:1455/auth/callback", id_token_hint="secret-jwt")
    q = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))
    assert q["client_id"] == "oaiapp_1" and "agent_name_hint" not in q
    assert "secret-jwt" not in oauth.redact_url(url)


def test_host_id_is_stable_across_loads(tmp_path):
    assert oauth.Registration.load(tmp_path).host_id == oauth.Registration.load(tmp_path).host_id


def test_each_attempt_has_fresh_state_nonce_and_verifier():
    a, b = oauth.AuthAttempt.new(), oauth.AuthAttempt.new()
    assert len({a.state, b.state}) == 2 and len({a.nonce, b.nonce}) == 2 and len({a.verifier, b.verifier}) == 2


# --- callback ---------------------------------------------------------------------------------------
def _hit(server, query: dict):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    opener.open(server.redirect_uri + "?" + urllib.parse.urlencode(query), timeout=5).read()


def test_callback_listener_falls_back_to_a_free_port_when_1455_is_busy():
    with oauth.CallbackServer(0) as first:
        with oauth.CallbackServer(first.port) as second:
            assert second.port != first.port
            assert second.redirect_uri == f"http://127.0.0.1:{second.port}/auth/callback"


def test_callback_validation_rejects_state_mismatch_denial_and_client_swap(tmp_path):
    attempt = oauth.AuthAttempt.new()
    reg = oauth.Registration.load(tmp_path)
    with pytest.raises(oauth.OAuthError, match="state"):
        oauth.validate_callback(oauth.CallbackResult({"code": "c", "state": "forged", "client_id": "oaiapp_1"}), attempt, reg)
    with pytest.raises(oauth.OAuthError, match="cancelled"):
        oauth.validate_callback(oauth.CallbackResult({"error": "access_denied", "state": attempt.state}), attempt, reg)
    with pytest.raises(oauth.OAuthError, match="issued client_id"):
        oauth.validate_callback(oauth.CallbackResult({"code": "c", "state": attempt.state}), attempt, reg)
    reg.client_id = "oaiapp_1"
    with pytest.raises(oauth.OAuthError, match="differs"):
        oauth.validate_callback(oauth.CallbackResult({"code": "c", "state": attempt.state, "client_id": "oaiapp_2"}), attempt, reg)
    assert oauth.validate_callback(oauth.CallbackResult({"code": "c", "state": attempt.state}), attempt, reg) == ("c", "oaiapp_1")


def test_full_login_flow_stores_tokens_encrypted_with_0600(stub, store, tmp_path):
    attempt = oauth.AuthAttempt.new()
    stub.nonce = attempt.nonce
    reg = oauth.Registration.load(tmp_path)
    with oauth.CallbackServer(0) as server:
        _hit(server, {"code": "auth-code", "state": attempt.state, "client_id": "oaiapp_1"})
        result = server.wait(5)
    code, client_id = oauth.validate_callback(result, attempt, reg)
    saved = oauth.exchange_code(Http(), stub.endpoints, client_id=client_id, code=code, attempt=attempt, redirect_uri=server.redirect_uri, now=NOW)
    store.save(saved)

    sent = stub.token_requests[0]
    assert sent == {"grant_type": "authorization_code", "client_id": "oaiapp_1", "code": "auth-code", "code_verifier": attempt.verifier,
                    "redirect_uri": server.redirect_uri, "resource": "https://api.openai.com/v1"}
    loaded = store.load()
    assert loaded.access_token == "access-1" and loaded.account_key == "account:ws-1" and loaded.expires_at == NOW + 3600
    raw = store.path.read_bytes()
    assert b"access-1" not in raw and b"refresh-1" not in raw
    assert stat.S_IMODE(os.stat(store.path).st_mode) == 0o600
    assert stat.S_IMODE(os.stat(store.key_path).st_mode) == 0o600


def test_login_fails_closed_without_direct_use_scope_or_with_wrong_nonce(stub):
    attempt = oauth.AuthAttempt.new()
    stub.nonce = attempt.nonce
    stub.scope = "openid profile email offline_access"
    with pytest.raises(oauth.OAuthError, match="chatgpt.tokens.use.direct"):
        oauth.exchange_code(Http(), stub.endpoints, client_id="oaiapp_1", code="c", attempt=attempt, redirect_uri="x", now=NOW)
    stub.scope = oauth.SCOPE
    stub.nonce = "other"
    with pytest.raises(oauth.OAuthError, match="nonce"):
        oauth.exchange_code(Http(), stub.endpoints, client_id="oaiapp_1", code="c", attempt=attempt, redirect_uri="x", now=NOW)


# --- refresh / 401 ----------------------------------------------------------------------------------
def test_token_not_refreshed_with_more_than_5_minutes_left(stub, store):
    store.save(creds(expires_at=NOW + 301))
    session = ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW)
    assert session.access_token() == "access-0" and stub.token_requests == []


def test_token_refreshed_when_under_5_minutes_and_rotation_persisted(stub, store):
    store.save(creds(expires_at=NOW + 299))
    session = ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW)
    assert session.access_token() == "access-1"
    sent = stub.token_requests[0]
    assert sent == {"grant_type": "refresh_token", "client_id": "oaiapp_1", "refresh_token": "refresh-0", "resource": "https://api.openai.com/v1"}
    saved = store.load()
    assert (saved.refresh_token, saved.expires_at, saved.account_key) == ("refresh-1", NOW + 3600, "account:ws-1")


def test_inference_401_forces_one_refresh_and_retries_once(stub, store):
    store.save(creds())
    stub.unauthorized_remaining = 1
    session = ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW)
    response = ChatGPTProvider(ResponsesClient(session, Http()), "gpt-5.5", "medium").review({"prompt": "p", "response": "r"})
    assert response.violation is None and stub.responses_auth == ["Bearer access-0", "Bearer access-1"]
    assert session.refresh_count == 1


def test_second_401_is_not_retried_again(stub, store):
    store.save(creds())
    stub.unauthorized_remaining = 2
    session = ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW)
    with pytest.raises(ProviderError):
        ChatGPTProvider(ResponsesClient(session, Http()), "gpt-5.5", None).review({"prompt": "p", "response": "r"})
    assert len(stub.responses_auth) == 2


def test_dead_refresh_token_asks_for_login_again(stub, store):
    store.save(creds(expires_at=NOW, refresh="dead"))
    with pytest.raises(oauth.OAuthError, match="login"):
        ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW).access_token()


def test_logout_revokes_refresh_token(stub, store):
    store.save(creds())
    assert oauth.revoke(Http(), stub.endpoints, store.load())
    assert stub.revoked == [{"token": "refresh-0", "token_type_hint": "refresh_token", "client_id": "oaiapp_1"}]
    store.delete()
    assert store.load() is None and not store.key_path.exists()


# --- model catalog ----------------------------------------------------------------------------------
def test_catalog_keeps_listed_models_and_parses_optional_fields(stub, store):
    from labelhub_evals.chatgpt.models import fetch_catalog

    store.save(creds())
    models = fetch_catalog(ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW), Http(), stub.endpoints)
    assert [m.slug for m in models] == ["gpt-5.4", "gpt-5.5", "gpt-5.5-mini"]
    assert models[0].reasoning_efforts == ("low", "medium") and models[0].default_effort == "medium"
    assert models[2].reasoning_efforts == () and models[2].display_name == "gpt-5.5-mini"


def test_new_catalog_models_are_picked_up_without_code_changes():
    models = parse_catalog({"models": [{"slug": "gpt-5.5", "priority": 1}, {"slug": "gpt-6", "priority": 1}]})
    assert pick_latest(models).slug == "gpt-6"


def test_auto_latest_prefers_priority_then_newer_version():
    models = parse_catalog({"data": [
        {"slug": "gpt-5.4", "priority": 1}, {"slug": "gpt-5.5", "priority": 1},
        {"slug": "gpt-6", "priority": 2}, {"slug": "gpt-5", "priority": 1}, {"slug": "o9"},
    ]})
    assert [m.slug for m in ranked(models)] == ["gpt-5.5", "gpt-5.4", "gpt-5", "gpt-6", "o9"]


def test_models_requiring_newer_client_are_not_selectable():
    models = [ModelInfo("gpt-7", "", (), None, 0, "99.0.0"), ModelInfo("gpt-5.5", "", (), None, 1, "0.1.0")]
    assert pick_latest(models, "0.1.0").slug == "gpt-5.5"


# --- draft -> test -> apply -> rollback -----------------------------------------------------------------
def test_apply_requires_a_passing_test_of_the_exact_draft(tmp_path):
    profiles = ProfileStore(tmp_path)
    profiles.select("account:a", Selection("gpt-5.5", "medium"), "manual")
    with pytest.raises(ProfileError, match="not passed"):
        profiles.apply("account:a")
    profiles.record_test("account:a", False, "boom")
    with pytest.raises(ProfileError, match="not passed"):
        profiles.apply("account:a")
    assert profiles.active() is None  # failed test keeps the current configuration


def test_select_test_apply_then_rollback(tmp_path):
    profiles = ProfileStore(tmp_path)
    for model in ("gpt-5.4", "gpt-5.5"):
        profiles.select("account:a", Selection(model, "medium"), "manual")
        profiles.record_test("account:a", True, "ok")
        profiles.apply("account:a")
    assert profiles.active().identity() == "chatgpt/gpt-5.5@medium"
    assert len(profiles.load()["rollback"]) == 1
    profiles.rollback("account:a")
    assert profiles.active().identity() == "chatgpt/gpt-5.4@medium"
    with pytest.raises(ProfileError, match="no rollback"):
        profiles.rollback("account:a")


def test_apply_rejects_draft_changed_after_test(tmp_path):
    profiles = ProfileStore(tmp_path)
    profiles.select("account:a", Selection("gpt-5.5", "low"), "manual")
    profiles.record_test("account:a", True, "ok")
    profile = profiles.load()
    profile["draft"]["effort"] = "high"  # e.g. hand-edited file
    (tmp_path / "chatgpt-profile.json").write_text(json.dumps(profile))
    with pytest.raises(ProfileError, match="draft changed"):
        profiles.apply("account:a")


def test_apply_rejects_other_account(tmp_path):
    profiles = ProfileStore(tmp_path)
    profiles.select("account:a", Selection("gpt-5.5", None), "manual")
    profiles.record_test("account:a", True, "ok")
    with pytest.raises(ProfileError, match="account"):
        profiles.apply("account:b")


def test_apply_rejects_profile_version_moved_after_test(tmp_path):
    profiles = ProfileStore(tmp_path)
    profiles.select("account:a", Selection("gpt-5.5", None), "manual")
    profiles.record_test("account:a", True, "ok")
    profile = profiles.load()
    profile["version"] += 1  # a concurrent writer saved in between
    (tmp_path / "chatgpt-profile.json").write_text(json.dumps(profile))
    with pytest.raises(ProfileError, match="changed after the test"):
        profiles.apply("account:a")


def test_switching_account_drops_selection_tested_elsewhere(tmp_path):
    profiles = ProfileStore(tmp_path)
    profiles.select("account:a", Selection("gpt-5.5", None), "manual")
    profiles.record_test("account:a", True, "ok")
    profiles.apply("account:a")
    profiles.select("account:b", Selection("gpt-5.4", None), "manual")
    profile = profiles.load()
    assert profile["active"] is None and profile["rollback"] == [] and profile["account_key"] == "account:b"


# --- streaming --------------------------------------------------------------------------------------
def test_stream_emits_text_deltas_and_collects_tool_call_and_usage(stub, store):
    store.save(creds())
    client = ResponsesClient(ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW), Http())
    deltas = []
    result = client.stream({"model": "gpt-5.5", "input": []}, deltas.append)
    assert deltas == ["Hel", "lo"] and result.text == "Hello"
    assert result.usage == {"input_tokens": 120, "output_tokens": 30}
    assert result.function_calls[0]["name"] == review.TOOL_NAME


def test_stream_without_completed_event_is_an_error():
    with pytest.raises(ProviderError, match="response.completed"):
        consume(iter_sse(["data: " + json.dumps({"type": "response.output_text.delta", "delta": "x"}), ""]))


def test_usage_limit_failure_is_reported_as_such():
    event = {"type": "response.failed", "response": {"error": {"code": "subscription_sharing_usage_limit_exceeded"}}}
    with pytest.raises(ProviderError, match="usage limit"):
        consume(iter_sse(["data: " + json.dumps(event), ""]))


def test_answer_without_tool_call_is_recorded_as_format_violation(stub, store):
    store.save(creds())
    stub.sse_events = [{"type": "response.output_text.delta", "delta": "looks fine"}, {"type": "response.completed", "response": {"usage": {}}}]
    response = ChatGPTProvider(ResponsesClient(ChatGPTSession(store, Http(), stub.endpoints, clock=lambda: NOW), Http()), "gpt-5.5", None).review({})
    assert response.arguments is None and "record_ai_review" in response.violation


def test_review_request_forces_the_tool_and_disables_storage():
    from labelhub_evals.chatgpt.responses import review_body

    body = review_body("gpt-5.5", "high", {"prompt": "p", "response": "r"})
    assert body["tool_choice"] == {"type": "function", "name": review.TOOL_NAME}
    assert body["tools"][0]["parameters"] == review.tool_parameters()
    assert body["reasoning"] == {"effort": "high"}


def test_http_error_never_echoes_tokens():
    err = HttpError(401, b'{"error":"x"}', "https://api.openai.com/v1/responses?access_token=leak")
    assert "leak" not in str(err)


# --- catalog robustness (first real-account run returned no usable model) ---------------------------
def test_catalog_accepts_standard_openai_list_shape_and_case_insensitive_visibility():
    from labelhub_evals.chatgpt.models import parse_catalog

    standard = {"object": "list", "data": [{"id": "gpt-5.5", "object": "model", "owned_by": "openai"}]}
    assert [m.slug for m in parse_catalog(standard)] == ["gpt-5.5"]
    assert [m.slug for m in parse_catalog({"models": [{"slug": "a", "visibility": "LIST"}, {"slug": "b", "visibility": "hide"}]})] == ["a"]
    assert [m.slug for m in parse_catalog([{"model": "c"}])] == ["c"]


def test_empty_catalog_is_explained_not_silent():
    from labelhub_evals.chatgpt.models import diagnose_catalog

    text = diagnose_catalog({"data": [{"slug": "x", "visibility": "hidden"}, {"visibility": "list"}]})
    assert "2 catalog entries" in text and "'hidden': 1" in text and "no slug/id: 1" in text
    assert "top-level keys: foo" in diagnose_catalog({"foo": 1})


def _cli(stub, tmp_path, monkeypatch, *argv):
    from labelhub_evals import cli

    monkeypatch.setenv("LABELHUB_EVALS_HOME", str(tmp_path))
    monkeypatch.setenv("LABELHUB_EVALS_AUTH_BASE", stub.base)
    monkeypatch.setenv("LABELHUB_EVALS_API_BASE", stub.base + "/v1")
    monkeypatch.setenv("LABELHUB_EVALS_NO_KEYRING", "1")
    return cli.main(list(argv))


def test_models_command_reports_why_catalog_is_empty_and_raw_dumps_it(stub, tmp_path, monkeypatch, capsys):
    CredentialsStore(tmp_path, use_keyring=False).save(creds(expires_at=10**10))
    stub.catalog = {"data": [{"slug": "gpt-x", "visibility": "hidden"}]}
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "models") == 1
    out = capsys.readouterr().out
    assert "no usable model" in out and "'hidden': 1" in out and "--skip-catalog" in out
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "models", "--raw") == 0
    assert json.loads(capsys.readouterr().out) == stub.catalog


def test_select_skip_catalog_still_requires_a_passing_test(stub, tmp_path, monkeypatch, capsys):
    CredentialsStore(tmp_path, use_keyring=False).save(creds(expires_at=10**10))
    stub.catalog = {"data": []}
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "select", "--auto-latest") == 1  # clean error, no traceback
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "select", "gpt-5.5", "--skip-catalog") == 0
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "apply") == 1
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "test") == 0
    assert _cli(stub, tmp_path, monkeypatch, "chatgpt", "apply") == 0
    assert ProfileStore(tmp_path).active().identity() == "chatgpt/gpt-5.5"
