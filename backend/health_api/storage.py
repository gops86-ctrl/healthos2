from __future__ import annotations

import json
import os
from typing import Any

from upstash_redis import Redis

SNAPSHOT_KEY = os.getenv("HEALTHOS_SNAPSHOT_REDIS_KEY", "healthos:canonical:snapshot")


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
