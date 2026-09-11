from __future__ import annotations

import hashlib
import json
import os
import threading
from datetime import date, datetime, time, timedelta, timezone
from typing import Any

from myfitnesspal_mcp import auth, config, mfp_client, refresh

from health_api.storage import load_mfp_session, save_mfp_session


DEFAULT_IMPERSONATE = "chrome"
_REFRESH_LOCK = threading.Lock()
MFP_URL = "https://www.myfitnesspal.com/"
SETTLE_MS = 4000


def _local_saved_cookies() -> dict[str, str] | None:
    try:
        path = config.cookies_path()
        if path.exists():
            saved = json.loads(path.read_text())
            cookies = saved.get("cookies")
            if isinstance(cookies, dict) and cookies:
                return {str(key): str(value) for key, value in cookies.items()}
    except Exception:
        pass
    return None


def _cookie_candidates() -> list[dict[str, str]]:
    """Return persistent, local, and bootstrap sessions without exposing them."""
    candidates: list[dict[str, str]] = []
    for cookies in (load_mfp_session(), _local_saved_cookies(), auth.load_cookies()):
        if not cookies:
            continue
        normalized = {str(key): str(value) for key, value in cookies.items()}
        if normalized not in candidates:
            candidates.append(normalized)
    return candidates


def _load_cookies() -> dict[str, str] | None:
    candidates = _cookie_candidates()
    return candidates[0] if candidates else None


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


def _browser_refresh(seed_cookies: dict[str, str]) -> dict[str, str]:
    """Visit MFP with a candidate session and harvest any rotated cookies.

    Mason's refresh helper uses the same Playwright flow, but Render needs
    Chromium launched with container-safe flags. Keeping the browser visit
    here also lets us report the real launch failure instead of mislabelling
    every browser exception as a missing Chromium installation.
    """
    if not refresh.available():
        raise RuntimeError("MyFitnessPal automatic refresh is unavailable")

    from playwright.sync_api import sync_playwright

    profile_dir = refresh.profile_dir()
    profile_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as pw:
        context = pw.chromium.launch_persistent_context(
            str(profile_dir),
            headless=True,
            chromium_sandbox=False,
            args=["--no-sandbox", "--disable-dev-shm-usage"],
        )
        try:
            context.add_cookies(
                [
                    {
                        "name": name,
                        "value": value,
                        "domain": ".myfitnesspal.com",
                        "path": "/",
                        "secure": True,
                    }
                    for name, value in seed_cookies.items()
                ]
            )
            page = context.pages[0] if context.pages else context.new_page()
            page.goto(MFP_URL, wait_until="domcontentloaded", timeout=30000)
            page.wait_for_timeout(SETTLE_MS)
            harvested: dict[str, str] = {}
            for cookie in context.cookies(MFP_URL):
                harvested[cookie["name"]] = cookie["value"]
            return harvested
        finally:
            context.close()


def _refresh_client(
    seed_cookies: dict[str, str],
    username: str | None,
    impersonate: str,
) -> mfp_client.CurlCffiClient:
    """Refresh MFP using the preinstalled Playwright browser and persist the result."""
    with _REFRESH_LOCK:
        try:
            cookies = _browser_refresh(seed_cookies)
        except Exception as exc:
            message = str(exc)
            if "Executable doesn't exist" in message or "executable doesn't exist" in message:
                raise RuntimeError(
                    "MyFitnessPal automatic refresh needs Chromium installed during the Render build"
                ) from exc
            raise RuntimeError(f"MyFitnessPal browser refresh failed: {message}") from exc

        if auth.SESSION_COOKIE not in cookies:
            raise RuntimeError(
                "MyFitnessPal session refresh did not produce a usable session cookie; "
                "the current MFP session may be fully expired and require re-authentication"
            )

        auth.save_cookies(cookies)
        save_mfp_session(cookies)
        mfp_client.reset()
        return mfp_client.build_client(cookies, username=username, impersonate=impersonate)


def _client() -> mfp_client.CurlCffiClient:
    candidates = _cookie_candidates()
    if not candidates:
        raise RuntimeError("MyFitnessPal is not configured; set MFP_COOKIE and retry")

    username = os.getenv("MFP_USERNAME", "").strip() or auth.saved_username()
    impersonate = os.getenv("MFP_IMPERSONATE", DEFAULT_IMPERSONATE).strip() or DEFAULT_IMPERSONATE
    refresh_errors: list[str] = []

    for cookies in candidates:
        try:
            return mfp_client.build_client(cookies, username=username, impersonate=impersonate)
        except Exception:
            try:
                return _refresh_client(cookies, username, impersonate)
            except Exception as refresh_error:
                refresh_errors.append(str(refresh_error))

    detail = next((error for error in refresh_errors if error), "unknown refresh error")
    raise RuntimeError(
        "Unable to authenticate to MyFitnessPal. The configured session may have expired. "
        "Automatic refresh failed: " + detail
    )


def _best_effort_session_touch(
    cookies: dict[str, str], username: str | None, impersonate: str
) -> None:
    """Refresh a still-valid browser session without making sync depend on it."""
    try:
        _refresh_client(cookies, username, impersonate)
    except Exception:
        # A successful diary sync is still useful. Refresh is an optimization
        # for extending the next session window, not a reason to fail the sync.
        pass


def fetch_recent_nutrition(days: int) -> list[dict[str, Any]]:
    if days not in (7, 30):
        raise ValueError("days must be 7 or 30")

    today = date.today()
    start = today - timedelta(days=days - 1)
    candidates = _cookie_candidates()
    if not candidates:
        raise RuntimeError("MyFitnessPal is not configured; set MFP_COOKIE and retry")

    username = os.getenv("MFP_USERNAME", "").strip() or auth.saved_username()
    impersonate = os.getenv("MFP_IMPERSONATE", DEFAULT_IMPERSONATE).strip() or DEFAULT_IMPERSONATE
    client = _client()
    active_cookies = candidates[0]
    records: list[dict[str, Any]] = []
    refreshed = False

    for offset in range(days):
        day = start + timedelta(days=offset)
        try:
            mfp_day = client.get_date(day)
        except Exception as exc:
            if refreshed:
                raise RuntimeError(f"MyFitnessPal diary fetch failed for {day}: {exc}") from exc
            refresh_errors: list[str] = []
            try:
                candidates = _cookie_candidates()
                if not candidates:
                    raise RuntimeError("MyFitnessPal is not configured; set MFP_COOKIE and retry")
                for cookies in candidates:
                    try:
                        client = _refresh_client(cookies, username, impersonate)
                        active_cookies = cookies
                        mfp_day = client.get_date(day)
                        refreshed = True
                        break
                    except Exception as refresh_error:
                        refresh_errors.append(str(refresh_error))
                else:
                    detail = next((error for error in refresh_errors if error), "unknown refresh error")
                    raise RuntimeError(detail)
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

    # Keep a still-valid session warm after a successful sync. If MFP has
    # already invalidated it, don't turn a successful data sync into a failure.
    _best_effort_session_touch(active_cookies, username, impersonate)
    return records
