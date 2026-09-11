"""Persistent Garmin token storage backed by Upstash Redis.

Render Free has an ephemeral filesystem, so the Garmin refresh-token store
must live outside the service. The local filesystem is used only temporarily
while python-garminconnect reads and writes its normal token-store format.
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

from cryptography.fernet import Fernet
from upstash_redis import Redis

TOKEN_KEY = os.getenv("GARMIN_TOKEN_REDIS_KEY", "healthos:garmin:tokens")


def _redis() -> Redis:
    return Redis.from_env()


def _fernet() -> Fernet:
    key = os.getenv("GARMIN_TOKEN_ENCRYPTION_KEY", "").strip()
    if not key:
        raise RuntimeError("GARMIN_TOKEN_ENCRYPTION_KEY is not configured")
    try:
        return Fernet(key.encode("ascii"))
    except Exception as exc:
        raise RuntimeError("GARMIN_TOKEN_ENCRYPTION_KEY is not a valid Fernet key") from exc


def token_store_configured() -> bool:
    return bool(os.getenv("UPSTASH_REDIS_REST_URL") and os.getenv("UPSTASH_REDIS_REST_TOKEN"))


def load_token_json() -> str | None:
    value = _redis().get(TOKEN_KEY)
    if value is None:
        return None
    try:
        return _fernet().decrypt(str(value).encode("ascii")).decode("utf-8")
    except Exception as exc:
        raise RuntimeError("Stored Garmin token data could not be decrypted") from exc


def save_token_json(token_json: str) -> None:
    encrypted = _fernet().encrypt(token_json.encode("utf-8")).decode("ascii")
    _redis().set(TOKEN_KEY, encrypted)


def create_temporary_token_store(token_json: str | None) -> tempfile.TemporaryDirectory[str]:
    directory = tempfile.TemporaryDirectory(prefix="healthos-garmin-")
    if token_json is not None:
        path = Path(directory.name) / "garmin_tokens.json"
        path.write_text(token_json, encoding="utf-8")
        path.chmod(0o600)
    return directory


def read_temporary_token_store(directory: str) -> str:
    path = Path(directory) / "garmin_tokens.json"
    if not path.exists():
        raise RuntimeError("Garmin did not create a token store")
    return path.read_text(encoding="utf-8")


def generate_encryption_key() -> str:
    return Fernet.generate_key().decode("ascii")
