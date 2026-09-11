"""Small HTTP service that exposes the Garmin adapter to HealthOS Android."""

from __future__ import annotations

import os
from datetime import date, timedelta
from typing import Any
from xml.etree import ElementTree

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from garminconnect import Garmin

from garmin_adapter import GarminAdapter
from garmin_api.token_store import (
    create_temporary_token_store,
    load_token_json,
    read_temporary_token_store,
    save_token_json,
)

app = FastAPI(title="HealthOS Garmin API", version="0.4.0")
API_KEY = os.getenv("HEALTHOS_API_KEY", "").strip()


class SyncRequest(BaseModel):
    start: str | None = Field(default=None, description="YYYY-MM-DD")
    end: str | None = Field(default=None, description="YYYY-MM-DD")


def _check_api_key(value: str | None) -> None:
    if API_KEY and value != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")


def _default_range() -> tuple[str, str]:
    end = date.today()
    start = end - timedelta(days=6)
    return start.isoformat(), end.isoformat()


def _client_from_redis() -> tuple[Garmin, Any]:
    token_json = load_token_json()
    if token_json is None:
        raise HTTPException(
            status_code=503,
            detail="Garmin token store is not configured. Run backend/garmin_api/login.py locally first.",
        )

    try:
        temporary_store = create_temporary_token_store(token_json)
        client = Garmin(retry_attempts=3)
        client.login(temporary_store.name)
        return client, temporary_store
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Garmin authentication failed: {exc}") from exc


def _persist_refreshed_tokens(temporary_store: Any) -> None:
    try:
        save_token_json(read_temporary_token_store(temporary_store.name))
    finally:
        temporary_store.cleanup()


def _resolve_range(request: SyncRequest) -> tuple[str, str]:
    default_start, default_end = _default_range()
    return request.start or default_start, request.end or default_end


def _route_points_from_activity_file(payload: bytes | str | None) -> str | None:
    if not payload:
        return None
    try:
        root = ElementTree.fromstring(payload)
    except ElementTree.ParseError:
        return None

    def local_name(tag: str) -> str:
        return tag.rsplit("}", 1)[-1]

    points: list[tuple[float, float]] = []
    for element in root.iter():
        if local_name(element.tag) == "trkpt":
            lat = element.attrib.get("lat")
            lon = element.attrib.get("lon")
            try:
                if lat is not None and lon is not None:
                    points.append((float(lat), float(lon)))
            except ValueError:
                continue
        elif local_name(element.tag) == "Trackpoint":
            latitude = None
            longitude = None
            for child in element.iter():
                name = local_name(child.tag)
                if name == "LatitudeDegrees":
                    try:
                        latitude = float(child.text or "")
                    except ValueError:
                        latitude = None
                elif name == "LongitudeDegrees":
                    try:
                        longitude = float(child.text or "")
                    except ValueError:
                        longitude = None
            if latitude is not None and longitude is not None:
                points.append((latitude, longitude))

    if len(points) < 2:
        return None
    max_points = 500
    if len(points) > max_points:
        last = len(points) - 1
        points = [points[int(i * last / (max_points - 1))] for i in range(max_points)]
    return ";".join(f"{lat},{lon}" for lat, lon in points)


def _download_route_points(client: Any, activity_id: str) -> str | None:
    """Download the Garmin TCX export and reduce its GPS track to route points."""
    try:
        downloader = getattr(client, "download_activity")
        payload = downloader(str(activity_id))
        return _route_points_from_activity_file(payload)
    except Exception:
        # Indoor activities, pool swims and some older activities may not have
        # an exportable GPS track. Summary activity data should still sync.
        return None


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/garmin/sync")
def sync_garmin(
    request: SyncRequest,
    x_healthos_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    _check_api_key(x_healthos_api_key)
    start, end = _resolve_range(request)

    temporary_store = None
    try:
        client, temporary_store = _client_from_redis()
        metrics = GarminAdapter(client).sync(start, end)
        _persist_refreshed_tokens(temporary_store)
        temporary_store = None
    except HTTPException:
        if temporary_store is not None:
            temporary_store.cleanup()
        raise
    except Exception as exc:
        if temporary_store is not None:
            temporary_store.cleanup()
        raise HTTPException(status_code=502, detail=f"Garmin sync failed: {exc}") from exc

    return {
        "source": "GARMIN",
        "start": start,
        "end": end,
        "metrics": [
            {
                "type": item.metric_type,
                "value": item.value,
                "unit": item.unit,
                "date": item.date,
                "sourceRecordId": f"garmin:{item.source_record_id}",
            }
            for item in metrics
        ],
    }


@app.post("/garmin/activities")
def sync_garmin_activities(
    request: SyncRequest,
    x_healthos_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Return recent Garmin activities, excluding strength/workout for V1."""
    _check_api_key(x_healthos_api_key)
    start, end = _resolve_range(request)

    temporary_store = None
    try:
        client, temporary_store = _client_from_redis()
        activities = GarminAdapter(client).sync_activities(start, end)
        response_activities = []
        for item in activities:
            route_points = _download_route_points(client, item.source_record_id)
            response_activities.append(
                {
                    "activityType": item.activity_type,
                    "name": item.name,
                    "durationSeconds": item.duration_seconds,
                    "elapsedDurationSeconds": item.elapsed_duration_seconds,
                    "distanceMeters": item.distance_meters,
                    "averageHeartRate": item.average_heart_rate,
                    "maxHeartRate": item.max_heart_rate,
                    "averageSpeedMps": item.average_speed_mps,
                    "elevationGainMeters": item.elevation_gain_meters,
                    "calories": item.calories,
                    "routePoints": route_points,
                    "recordedAtMillis": item.recorded_at_millis,
                    "sourceRecordId": f"garmin:{item.source_record_id}",
                }
            )
        _persist_refreshed_tokens(temporary_store)
        temporary_store = None
    except HTTPException:
        if temporary_store is not None:
            temporary_store.cleanup()
        raise
    except Exception as exc:
        if temporary_store is not None:
            temporary_store.cleanup()
        raise HTTPException(status_code=502, detail=f"Garmin activity sync failed: {exc}") from exc

    return {
        "source": "GARMIN",
        "start": start,
        "end": end,
        "activities": response_activities,
    }
