from __future__ import annotations

import os
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, Field

from health_api.hevy import configured as hevy_configured
from health_api.hevy import fetch_recent_workouts
from health_api.storage import load_snapshot, save_snapshot, storage_configured

app = FastAPI(title="HealthOS Data API", version="0.2.0")


class HealthSnapshot(BaseModel):
    """Canonical payload uploaded by an authenticated HealthOS client."""

    metrics: list[dict[str, Any]] = Field(default_factory=list)
    activities: list[dict[str, Any]] = Field(default_factory=list)
    workouts: list[dict[str, Any]] = Field(default_factory=list)
    nutrition: list[dict[str, Any]] = Field(default_factory=list)
    body_measurements: list[dict[str, Any]] = Field(default_factory=list)
    labs: list[dict[str, Any]] = Field(default_factory=list)
    profile: dict[str, Any] = Field(default_factory=dict)


def _check_api_key(value: str | None) -> None:
    expected = os.getenv("HEALTHOS_API_KEY", "").strip()
    if expected and value != expected:
        raise HTTPException(status_code=401, detail="Invalid API key")


def _require_storage() -> None:
    if not storage_configured():
        raise HTTPException(status_code=503, detail="HealthOS storage is not configured")


def _filter_records(
    records: list[dict[str, Any]],
    start_millis: int | None,
    end_millis: int | None,
) -> list[dict[str, Any]]:
    if start_millis is None and end_millis is None:
        return records
    result: list[dict[str, Any]] = []
    for record in records:
        timestamp = record.get("recordedAtMillis", record.get("recorded_at_millis"))
        if not isinstance(timestamp, (int, float)):
            continue
        if start_millis is not None and timestamp < start_millis:
            continue
        if end_millis is not None and timestamp > end_millis:
            continue
        result.append(record)
    return result


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "ok", "storage": "configured" if storage_configured() else "unconfigured", "hevy": "configured" if hevy_configured() else "unconfigured"}


@app.get("/health/hevy/workouts")
def get_hevy_workouts(
    days: int = Query(default=7, description="Recent Hevy history window: 7 or 30 days"),
    x_healthos_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Proxy recent Hevy workouts for the authenticated HealthOS app.

    Hevy credentials remain server-side. The Android app receives normalized-source
    candidates and performs local deduplication before writing them to Room.
    """
    _check_api_key(x_healthos_api_key)
    if days not in (7, 30):
        raise HTTPException(status_code=400, detail="days must be 7 or 30")
    try:
        workouts = fetch_recent_workouts(days)
    except Exception as exc:
        detail = str(exc).strip() or "Unable to fetch Hevy workouts"
        status = 503 if "not configured" in detail.lower() else 502
        raise HTTPException(status_code=status, detail=detail) from exc
    return {"source": "HEVY", "days": days, "count": len(workouts), "workouts": workouts}


@app.post("/health/snapshot")
def upload_snapshot(
    snapshot: HealthSnapshot,
    x_healthos_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Store the canonical HealthOS snapshot for API and MCP consumers."""
    _check_api_key(x_healthos_api_key)
    _require_storage()
    payload = snapshot.model_dump()
    save_snapshot(payload)
    return {
        "status": "stored",
        "counts": {key: len(value) if isinstance(value, list) else None for key, value in payload.items() if key != "profile"},
        "profile_present": bool(payload["profile"]),
    }


@app.get("/health/snapshot")
def get_snapshot(x_healthos_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    snapshot = load_snapshot()
    if snapshot is None:
        raise HTTPException(status_code=404, detail="No HealthOS snapshot has been uploaded")
    return snapshot


@app.get("/health/metrics")
def get_metrics(
    metric_type: str | None = Query(default=None),
    start_millis: int | None = Query(default=None),
    end_millis: int | None = Query(default=None),
    x_healthos_api_key: str | None = Header(default=None),
) -> list[dict[str, Any]]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    snapshot = load_snapshot() or {}
    records = snapshot.get("metrics", [])
    if metric_type:
        records = [r for r in records if str(r.get("metricType", r.get("metric_type", ""))).upper() == metric_type.upper()]
    return _filter_records(records, start_millis, end_millis)


@app.get("/health/activities")
def get_activities(
    activity_type: str | None = Query(default=None),
    start_millis: int | None = Query(default=None),
    end_millis: int | None = Query(default=None),
    x_healthos_api_key: str | None = Header(default=None),
) -> list[dict[str, Any]]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    snapshot = load_snapshot() or {}
    records = snapshot.get("activities", [])
    if activity_type:
        records = [r for r in records if str(r.get("activityType", r.get("activity_type", ""))).upper() == activity_type.upper()]
    return _filter_records(records, start_millis, end_millis)


@app.get("/health/workouts")
def get_workouts(
    start_millis: int | None = Query(default=None),
    end_millis: int | None = Query(default=None),
    x_healthos_api_key: str | None = Header(default=None),
) -> list[dict[str, Any]]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    return _filter_records((load_snapshot() or {}).get("workouts", []), start_millis, end_millis)


@app.get("/health/nutrition")
def get_nutrition(
    start_millis: int | None = Query(default=None),
    end_millis: int | None = Query(default=None),
    x_healthos_api_key: str | None = Header(default=None),
) -> list[dict[str, Any]]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    return _filter_records((load_snapshot() or {}).get("nutrition", []), start_millis, end_millis)


@app.get("/health/body")
def get_body(
    start_millis: int | None = Query(default=None),
    end_millis: int | None = Query(default=None),
    x_healthos_api_key: str | None = Header(default=None),
) -> list[dict[str, Any]]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    return _filter_records((load_snapshot() or {}).get("body_measurements", []), start_millis, end_millis)


@app.get("/health/labs")
def get_labs(
    start_millis: int | None = Query(default=None),
    end_millis: int | None = Query(default=None),
    x_healthos_api_key: str | None = Header(default=None),
) -> list[dict[str, Any]]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    return _filter_records((load_snapshot() or {}).get("labs", []), start_millis, end_millis)


@app.get("/health/profile")
def get_profile(x_healthos_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    _check_api_key(x_healthos_api_key)
    _require_storage()
    return (load_snapshot() or {}).get("profile", {})
