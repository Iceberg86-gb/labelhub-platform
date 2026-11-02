from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Endpoints:
    auth_base: str = "https://auth.openai.com"
    api_base: str = "https://api.openai.com/v1"

    @property
    def authorize(self) -> str:
        return self.auth_base + "/api/accounts/authorize"

    @property
    def token(self) -> str:
        return self.auth_base + "/api/accounts/oauth/token"

    @property
    def openid_configuration(self) -> str:
        return self.auth_base + "/.well-known/openid-configuration"

    @property
    def resource(self) -> str:
        # The audience the access token is minted for; always the public API, even against a stub.
        return "https://api.openai.com/v1"

    @staticmethod
    def from_env() -> "Endpoints":
        return Endpoints(
            os.environ.get("LABELHUB_EVALS_AUTH_BASE", Endpoints.auth_base).rstrip("/"),
            os.environ.get("LABELHUB_EVALS_API_BASE", Endpoints.api_base).rstrip("/"),
        )


def home_dir() -> Path:
    base = os.environ.get("LABELHUB_EVALS_HOME")
    path = Path(base) if base else Path.home() / ".config" / "labelhub-evals"
    path.mkdir(parents=True, exist_ok=True)
    try:
        os.chmod(path, 0o700)
    except OSError:
        pass
    return path
