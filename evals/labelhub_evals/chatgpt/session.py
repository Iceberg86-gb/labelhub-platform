"""Valid-token provider: refresh when < 5 minutes remain, force-refresh + retry once on 401 (ADR-018 §2.6)."""

from __future__ import annotations

import time
from typing import Callable, TypeVar

from labelhub_evals.chatgpt import oauth
from labelhub_evals.chatgpt.config import Endpoints
from labelhub_evals.chatgpt.credentials import Credentials, CredentialsStore
from labelhub_evals.chatgpt.http import Http, HttpError

REFRESH_SKEW_SECONDS = 300
T = TypeVar("T")


class NotSignedIn(RuntimeError):
    pass


class ChatGPTSession:
    def __init__(self, store: CredentialsStore, http: Http, endpoints: Endpoints, clock: Callable[[], float] = time.time):
        self.store, self.http, self.endpoints, self.clock = store, http, endpoints, clock
        self.refresh_count = 0

    def credentials(self) -> Credentials:
        creds = self.store.load()
        if creds is None:
            raise NotSignedIn("not signed in; run `python -m labelhub_evals chatgpt login`")
        return creds

    def _needs_refresh(self, creds: Credentials) -> bool:
        return creds.expires_at - self.clock() < REFRESH_SKEW_SECONDS

    def access_token(self, *, force_refresh: bool = False) -> str:
        creds = self.credentials()
        if not force_refresh and not self._needs_refresh(creds):
            return creds.access_token
        stale_token = creds.access_token
        with self.store.lock():
            creds = self.credentials()  # another process may have refreshed while we waited
            rotated_meanwhile = creds.access_token != stale_token
            if (force_refresh and not rotated_meanwhile) or (not force_refresh and self._needs_refresh(creds)):
                creds = oauth.refresh(self.http, self.endpoints, creds, now=self.clock())
                self.store.save(creds)  # access, refresh, expiry and scopes replaced together
                self.refresh_count += 1
            return creds.access_token

    def call(self, fn: Callable[[str], T]) -> T:
        """Run fn(access_token); on HTTP 401 force a refresh and retry exactly once."""
        try:
            return fn(self.access_token())
        except HttpError as exc:
            if exc.status != 401:
                raise
        return fn(self.access_token(force_refresh=True))
