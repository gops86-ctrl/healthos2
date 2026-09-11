from __future__ import annotations

import json
import os
from typing import Any

from upstash_redis import Redis

SNAPSHOT_KEY = os.getenv("HEALTHOS_SNAPSHOT_REDIS_KEY", "healthos:canonical:snapshot")
MFP_SESSION_KEY = os.getenv("HEALTHOS_MFP_SESSION_REDIS_KEY", "healthos:mfp:session")


def _redis() -> Redis:
    return Redis.from_env()


def storage_configured() -> bool:
    return bool(
        os.getenv("UPSTASH_REDIS_REST_URL", "").strip()
        and os.getenv("UPSTASH_REDIS_REST_TOKEN", "").strip()
    )


def load_snapshot() -> dict[str, Any] | None:
    value = _redis().get(SNAPSHOT_KEY)
    if value is None:
        return None
    return json.loads(str(value))


def save_snapshot(snapshot: dict[str, Any]) -> None:
    _redis().set(SNAPSHOT_KEY, json.dumps(snapshot, separators=(",", ":")))


def load_mfp_session() -> dict[str, str] | None:
    """Load the latest known MFP browser session from persistent Redis storage."""
    if not storage_configured():
        return None
    value = _redis().get(MFP_SESSION_KEY)
    if value is None:
        return None
    try:
        payload = json.loads(str(value))
    except (TypeError, ValueError):
        return None
    if not isinstance(payload, dict):
        return None
    cookies = payload.get("cookies")
    if not isinstance(cookies, dict):
        return None
    return {str(key): str(cookie) for key, cookie in cookies.items()}


def save_mfp_session(cookies: dict[str, str]) -> None:
    """Persist the latest working MFP session so Render restarts don't lose it."""
    if not storage_configured() or not cookies:
        return
    _redis().set(
        MFP_SESSION_KEY,
        json.dumps({"cookies": cookies}, separators=(",", ":")),
    )
