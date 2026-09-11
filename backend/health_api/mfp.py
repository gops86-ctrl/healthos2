from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
from datetime import date, datetime, time, timedelta, timezone
from typing import Any

from myfitnesspal_mcp import auth, config, mfp_client, refresh


DEFAULT_IMPERSONATE = "chrome"


def _load_cookies() -> dict[str, str] | None:
    """Prefer a refreshed cookie saved during this Render instance.

    MFP_COOKIE is still the bootstrap credential, but mfp-mcp's auth loader
    gives the environment variable precedence forever. That would discard a
    newly rotated session saved by the auto-refresh flow.
    """
    try:
        path = config.cookies_path()
        if path.exists():
            saved = json.loads(path.read_text())
            cookies = saved.get("cookies")
            if cookies:
                return cookies
    except Exception:
        pass
    return auth.load_cookies()


def configured() -> bool:
    return bool(_load_cookies())


def _as_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _macro_value(totals: Any, *keys: str) -> float | None:
    if not isinstance(totals, dict):
        return None
    for key in keys:
        value = _as_float(totals.get(key))
        if value is not None:
            return value
    return None


def _source_record_id(day: date, meal: str | None, name: str, index: int) -> str:
    raw = f"{day.isoformat()}|{meal or ''}|{name}|{index}".encode("utf-8")
    digest = hashlib.sha256(raw).hexdigest()[:20]
    return f"mfp-{day.isoformat()}-{digest}"


def _install_browser() -> None:
    subprocess.run(
        [sys.executable, "-m", "playwright", "install", "chromium"],
        check=True,
        timeout=180,
    )


def _refresh_client(
    seed_cookies: dict[str, str],
    username: str | None,
    impersonate: str,
) -> mfp_client.CurlCffiClient:
    if not refresh.available():
        raise RuntimeError("MyFitnessPal session expired and automatic browser refresh is unavailable")

    try:
        if not refresh.profile_seeded():
            refresh.seed_profile(seed_cookies)
        refresh.refresh_session()
    except Exception as first_error:
        message = str(first_error).lower()
        if "executable doesn't exist" not in message and "browser" not in message:
            raise
        _install_browser()
        # A failed first seed still creates the browser-profile directory, so
        # seed explicitly after installing Chromium instead of relying on the
        # directory-exists check.
        refresh.seed_profile(seed_cookies)
        refresh.refresh_session()

    cookies = _load_cookies() or seed_cookies
    return mfp_client.build_client(cookies, username=username, impersonate=impersonate)


def _client() -> mfp_client.CurlCffiClient:
    cookies = _load_cookies()
    if not cookies:
        raise RuntimeError("MyFitnessPal is not configured; set MFP_COOKIE and retry")

    username = os.getenv("MFP_USERNAME", "").strip() or auth.saved_username()
    impersonate = os.getenv("MFP_IMPERSONATE", DEFAULT_IMPERSONATE).strip() or DEFAULT_IMPERSONATE

    try:
        return mfp_client.build_client(cookies, username=username, impersonate=impersonate)
    except Exception as exc:
        try:
            return _refresh_client(cookies, username, impersonate)
        except Exception as refresh_error:
            raise RuntimeError(
                "Unable to authenticate to MyFitnessPal. The configured session cookie may have expired. "
                "Automatic refresh also failed: " + str(refresh_error)
            ) from exc


def fetch_recent_nutrition(days: int) -> list[dict[str, Any]]:
    if days not in (7, 30):
        raise ValueError("days must be 7 or 30")

    today = date.today()
    start = today - timedelta(days=days - 1)
    client = _client()
    records: list[dict[str, Any]] = []
    refreshed = False

    for offset in range(days):
        day = start + timedelta(days=offset)
        try:
            mfp_day = client.get_date(day)
        except Exception as exc:
            if refreshed:
                raise RuntimeError(f"MyFitnessPal diary fetch failed for {day}: {exc}") from exc
            try:
                cookies = _load_cookies()
                if not cookies:
                    raise RuntimeError("MyFitnessPal is not configured; set MFP_COOKIE and retry")
                username = os.getenv("MFP_USERNAME", "").strip() or auth.saved_username()
                impersonate = os.getenv("MFP_IMPERSONATE", DEFAULT_IMPERSONATE).strip() or DEFAULT_IMPERSONATE
                client = _refresh_client(cookies, username, impersonate)
                refreshed = True
                mfp_day = client.get_date(day)
            except Exception as refresh_error:
                raise RuntimeError(
                    f"MyFitnessPal diary fetch failed for {day}; session refresh failed: {refresh_error}"
                ) from exc

        entry_index = 0
        for meal in mfp_day.meals:
            meal_name = str(meal.name).title() if meal.name else None
            for entry in meal.entries:
                totals = entry.totals
                name = str(entry.name).strip()
                recorded_at = int(datetime.combine(day, time.min, tzinfo=timezone.utc).timestamp() * 1000)
                records.append(
                    {
                        "calories": _macro_value(totals, "calories"),
                        "proteinGrams": _macro_value(totals, "protein"),
                        "carbohydrateGrams": _macro_value(totals, "carbohydrates", "carbs"),
                        "fatGrams": _macro_value(totals, "fat"),
                        "meal": meal_name,
                        "saturatedFatGrams": _macro_value(totals, "saturated_fat", "saturated fat"),
                        "polyunsaturatedFatGrams": _macro_value(totals, "polyunsaturated_fat", "polyunsaturated fat"),
                        "monounsaturatedFatGrams": _macro_value(totals, "monounsaturated_fat", "monounsaturated fat"),
                        "transFatGrams": _macro_value(totals, "trans_fat", "trans fat"),
                        "cholesterolMg": _macro_value(totals, "cholesterol"),
                        "sodiumMg": _macro_value(totals, "sodium"),
                        "potassiumMg": _macro_value(totals, "potassium", "potass."),
                        "fiberGrams": _macro_value(totals, "fiber"),
                        "sugarGrams": _macro_value(totals, "sugar"),
                        "recordedAtMillis": recorded_at,
                        "source": "MYFITNESSPAL",
                        "sourceRecordId": _source_record_id(day, meal_name, name, entry_index),
                        "foodName": name,
                    }
                )
                entry_index += 1

    return records
