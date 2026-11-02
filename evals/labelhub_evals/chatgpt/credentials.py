"""Encrypted local token storage — the CredentialsManager equivalent (ADR-018 §2.5).

Tokens are encrypted with Fernet (AES-128-CBC + HMAC-SHA256). The key lives in the OS keychain when
the optional `keyring` package works, otherwise in a 0600 key file next to the tokens. Writes are
atomic (temp file + rename) with 0600 permissions. Refreshes are serialized with a file lock so two
processes never race a rotating refresh token.
"""

from __future__ import annotations

import contextlib
import json
import os
from dataclasses import asdict, dataclass, field
from pathlib import Path

from cryptography.fernet import Fernet, InvalidToken

KEYRING_SERVICE = "labelhub-evals"
KEYRING_USER = "credentials-key"


@dataclass
class Credentials:
    client_id: str
    account_key: str
    email: str | None
    access_token: str
    refresh_token: str
    id_token: str | None
    expires_at: int
    scopes: list[str] = field(default_factory=list)


def _atomic_write(path: Path, data: bytes) -> None:
    tmp = path.with_name(path.name + ".part")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as handle:
        handle.write(data)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(tmp, path)
    with contextlib.suppress(OSError):
        os.chmod(path, 0o600)


class CredentialsStore:
    def __init__(self, home: Path, *, use_keyring: bool = True):
        self.home = home
        self.path = home / "credentials.enc"
        self.key_path = home / "credentials.key"
        self.lock_path = home / "credentials.lock"
        self.use_keyring = use_keyring and os.environ.get("LABELHUB_EVALS_NO_KEYRING") != "1"

    # --- key management -------------------------------------------------------------------
    def _keyring(self):
        if not self.use_keyring:
            return None
        try:
            import keyring  # type: ignore
            from keyring.backends import fail  # type: ignore

            if isinstance(keyring.get_keyring(), fail.Keyring):
                return None
            return keyring
        except Exception:
            return None

    def key_backend(self) -> str:
        return "os-keychain" if self._keyring() else "key-file(0600)"

    def _key(self, create: bool) -> bytes | None:
        kr = self._keyring()
        if kr is not None:
            value = kr.get_password(KEYRING_SERVICE, KEYRING_USER)
            if value is None and create:
                value = Fernet.generate_key().decode("ascii")
                kr.set_password(KEYRING_SERVICE, KEYRING_USER, value)
            return value.encode("ascii") if value else None
        if self.key_path.exists():
            return self.key_path.read_bytes().strip()
        if not create:
            return None
        key = Fernet.generate_key()
        _atomic_write(self.key_path, key)
        return key

    # --- records --------------------------------------------------------------------------
    def load(self) -> Credentials | None:
        if not self.path.exists():
            return None
        key = self._key(create=False)
        if key is None:
            raise RuntimeError("credentials exist but the encryption key is missing; run `chatgpt logout` then log in again")
        try:
            data = json.loads(Fernet(key).decrypt(self.path.read_bytes()))
        except InvalidToken:
            raise RuntimeError("stored credentials cannot be decrypted; run `chatgpt logout` then log in again") from None
        return Credentials(**data)

    def save(self, credentials: Credentials) -> None:
        key = self._key(create=True)
        _atomic_write(self.path, Fernet(key).encrypt(json.dumps(asdict(credentials)).encode("utf-8")))

    def delete(self) -> None:
        for path in (self.path, self.key_path):
            with contextlib.suppress(FileNotFoundError):
                path.unlink()
        kr = self._keyring()
        if kr is not None:
            with contextlib.suppress(Exception):
                kr.delete_password(KEYRING_SERVICE, KEYRING_USER)

    @contextlib.contextmanager
    def lock(self):
        fd = os.open(self.lock_path, os.O_RDWR | os.O_CREAT, 0o600)
        try:
            try:
                import fcntl

                fcntl.flock(fd, fcntl.LOCK_EX)
            except ImportError:  # Windows: best effort, single process
                pass
            yield
        finally:
            os.close(fd)
