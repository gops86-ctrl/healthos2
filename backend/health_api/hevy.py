from __future__ import annotations

import json
import os
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx
from upstash_redis import Redis

BASE_URL = os.getenv("HEVY_BASE_URL", "https://api.hevyapp.com").rstrip("/")
WEB_API_KEY = os.getenv("HEVY_API_KEY", "shelobs_hevy_web")
TOKEN_KEY = os.getenv("HEVY_TOKEN_REDIS_KEY", "healthos:hevy:tokens")


def _timestamp(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value) if float(value) < 10_000_000_000 else float(value) / 1000.0
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            number = float(text)
            return number if number < 10_000_000_000 else number / 1000.0
        except ValueError:
            try:
                return datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp()
            except ValueError:
                return None
    return None


def _headers(access_token: str) -> dict[str, str]:
    headers = {"x-api-key": WEB_API_KEY, "Accept": "application/json"}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


def _redis() -> Redis | None:
    if not (os.getenv("UPSTASH_REDIS_REST_URL", "").strip() and os.getenv("UPSTASH_REDIS_REST_TOKEN", "").strip()):
        return None
    return Redis.from_env()


def _load_tokens() -> dict[str, Any]:
    redis = _redis()
    if redis is not None:
        value = redis.get(TOKEN_KEY)
        if value:
            try:
                parsed = json.loads(str(value))
                if isinstance(parsed, dict):
                    return parsed
            except json.JSONDecodeError:
                pass
    return {
        "access_token": os.getenv("HEVY_ACCESS_TOKEN", "").strip(),
        "refresh_token": os.getenv("HEVY_REFRESH_TOKEN", "").strip(),
        "expires_at": os.getenv("HEVY_EXPIRES_AT", "").strip(),
    }


def _save_tokens(tokens: dict[str, Any]) -> None:
    redis = _redis()
    if redis is not None:
        redis.set(TOKEN_KEY, json.dumps(tokens, separators=(",", ":")))


def _refresh_access_token(client: httpx.Client, tokens: dict[str, Any]) -> str:
    access = str(tokens.get("access_token", "")).strip()
    refresh = str(tokens.get("refresh_token", "")).strip()
    if not refresh:
        raise RuntimeError("Hevy refresh token is not configured")

    response = client.post(
        f"{BASE_URL}/auth/refresh_token",
        json={"refresh_token": refresh},
        headers=_headers(access),
    )
    response.raise_for_status()
    refreshed = response.json()
    new_access = str(refreshed.get("access_token", "")).strip()
    new_refresh = str(refreshed.get("refresh_token", refresh)).strip()
    if not new_access:
        raise RuntimeError("Hevy token refresh returned no access token")

    updated = {
        "access_token": new_access,
        "refresh_token": new_refresh,
        "expires_at": refreshed.get("expires_at", ""),
    }
    _save_tokens(updated)
    return new_access


def _get_access_token(client: httpx.Client) -> str:
    tokens = _load_tokens()
    access = str(tokens.get("access_token", "")).strip()
    refresh = str(tokens.get("refresh_token", "")).strip()
    expiry = _timestamp(tokens.get("expires_at"))

    # If expiry is unknown, do not assume the access token is still valid.
    # Refresh once so an old Render/Upstash session is replaced by a fresh
    # access/refresh pair. The refreshed expiry is persisted by _save_tokens.
    needs_refresh = (
        not access
        or not refresh
        or expiry is None
        or expiry <= datetime.now(timezone.utc).timestamp() + 60
    )
    if not needs_refresh:
        return access
    return _refresh_access_token(client, tokens)


def _get_with_refresh(client: httpx.Client, url: str, *, params: dict[str, Any] | None = None) -> httpx.Response:
    tokens = _load_tokens()
    access = str(tokens.get("access_token", "")).strip()
    if not access:
        access = _get_access_token(client)

    response = client.get(url, params=params, headers=_headers(access))
    if response.status_code != 401:
        return response

    # Access tokens may expire without an expiry value being persisted.
    # Refresh once, persist the rotated credentials, then retry the request.
    new_access = _refresh_access_token(client, tokens)
    return client.get(url, params=params, headers=_headers(new_access))


def configured() -> bool:
    tokens = _load_tokens()
    return bool(str(tokens.get("access_token", "")).strip() or str(tokens.get("refresh_token", "")).strip())


def fetch_recent_workouts(days: int) -> list[dict[str, Any]]:
    if days not in (7, 30):
        raise ValueError("days must be 7 or 30")
    cutoff = datetime.now(timezone.utc) - timedelta(days=days - 1)
    username = os.getenv("HEVY_USERNAME", "").strip()

    with httpx.Client(timeout=30.0) as client:
        access_token = _get_access_token(client)
        headers = _headers(access_token)
        if not username:
            account = client.get(f"{BASE_URL}/account", headers=headers)
            if account.status_code == 401:
                access_token = _refresh_access_token(client, _load_tokens())
                account = client.get(f"{BASE_URL}/account", headers=_headers(access_token))
            account.raise_for_status()
            username = str(account.json().get("username", "")).strip()
        if not username:
            raise RuntimeError("Unable to determine the Hevy username")

        workouts: list[dict[str, Any]] = []
        offset = 0
        while True:
            response = _get_with_refresh(
                client,
                f"{BASE_URL}/user_workouts_paged",
                # The private web endpoint currently rejects page sizes above 5.
                # Keep this separate from the public API's pageSize limit.
                params={"username": username, "limit": 5, "offset": offset},
            )
            response.raise_for_status()
            payload = response.json()
            page = payload.get("workouts", []) if isinstance(payload, dict) else []
            if not page:
                break
            reached_cutoff = False
            for workout in page:
                if not isinstance(workout, dict):
                    continue
                start = _timestamp(workout.get("start_time"))
                if start is None:
                    continue
                if datetime.fromtimestamp(start, tz=timezone.utc) >= cutoff:
                    workouts.append(workout)
                else:
                    reached_cutoff = True
            if reached_cutoff or len(page) < 5:
                break
            offset += len(page)
    return workouts
