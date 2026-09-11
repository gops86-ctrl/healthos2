from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx

BASE_URL = os.getenv("HEVY_BASE_URL", "https://api.hevyapp.com").rstrip("/")
WEB_API_KEY = os.getenv("HEVY_API_KEY", "shelobs_hevy_web")


def _timestamp(value: Any) -> float | None:
    if isinstance(value, (int, float)):
        return float(value) if float(value) < 10_000_000_000 else float(value) / 1000.0
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return float(text)
        except ValueError:
            pass
        try:
            return datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp()
        except ValueError:
            return None
    return None


def _headers(access_token: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {access_token}",
        "x-api-key": WEB_API_KEY,
        "Accept": "application/json",
    }


def configured() -> bool:
    return bool(os.getenv("HEVY_ACCESS_TOKEN", "").strip())


def fetch_recent_workouts(days: int) -> list[dict[str, Any]]:
    if days not in (7, 30):
        raise ValueError("days must be 7 or 30")
    access_token = os.getenv("HEVY_ACCESS_TOKEN", "").strip()
    if not access_token:
        raise RuntimeError("Hevy access token is not configured")

    cutoff = datetime.now(timezone.utc) - timedelta(days=days - 1)
    username = os.getenv("HEVY_USERNAME", "").strip()
    headers = _headers(access_token)

    with httpx.Client(timeout=30.0) as client:
        if not username:
            account = client.get(f"{BASE_URL}/account", headers=headers)
            account.raise_for_status()
            username = str(account.json().get("username", "")).strip()
        if not username:
            raise RuntimeError("Unable to determine the Hevy username")

        workouts: list[dict[str, Any]] = []
        offset = 0
        while True:
            response = client.get(
                f"{BASE_URL}/user_workouts_paged",
                params={"username": username, "limit": 10, "offset": offset},
                headers=headers,
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

            if reached_cutoff or len(page) < 10:
                break
            offset += len(page)

    return workouts
