from __future__ import annotations

import hashlib
import os
from datetime import date, datetime, time, timedelta, timezone
from typing import Any

from myfitnesspal_mcp import auth, mfp_client


DEFAULT_IMPERSONATE = "chrome"


def configured() -> bool:
    return bool(auth.load_cookies())


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
    # python-myfitnesspal does not expose the MFP diary entry id on Entry.
    # Keep a deterministic id so repeated 7/30-day refreshes replace the same
    # canonical records instead of creating duplicates.
    raw = f"{day.isoformat()}|{meal or ''}|{name}|{index}".encode("utf-8")
    digest = hashlib.sha256(raw).hexdigest()[:20]
    return f"mfp-{day.isoformat()}-{digest}"


def _client() -> mfp_client.CurlCffiClient:
    cookies = auth.load_cookies()
    if not cookies:
        raise RuntimeError("MyFitnessPal is not configured; set MFP_COOKIE and retry")

    username = os.getenv("MFP_USERNAME", "").strip() or auth.saved_username()
    impersonate = os.getenv("MFP_IMPERSONATE", DEFAULT_IMPERSONATE).strip() or DEFAULT_IMPERSONATE

    try:
        return mfp_client.build_client(
            cookies,
            username=username,
            impersonate=impersonate,
        )
    except Exception as exc:
        raise RuntimeError(f"Unable to authenticate to MyFitnessPal: {exc}") from exc


def fetch_recent_nutrition(days: int) -> list[dict[str, Any]]:
    if days not in (7, 30):
        raise ValueError("days must be 7 or 30")

    today = date.today()
    start = today - timedelta(days=days - 1)
    client = _client()
    records: list[dict[str, Any]] = []

    for offset in range(days):
        day = start + timedelta(days=offset)
        try:
            mfp_day = client.get_date(day)
        except Exception as exc:
            raise RuntimeError(f"MyFitnessPal diary fetch failed for {day}: {exc}") from exc

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
