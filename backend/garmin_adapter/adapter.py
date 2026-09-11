"""Garmin Connect -> HealthOS metric and activity normalization adapter."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Any, Protocol


class GarminClient(Protocol):
    def get_daily_steps(self, start: str, end: str) -> list[dict[str, Any]]: ...
    def get_rhr_daily(self, start: str, end: str) -> list[dict[str, Any]]: ...
    def get_sleep_daily(self, start: str, end: str) -> list[dict[str, Any]]: ...
    def get_hrv_data_range(self, start: str, end: str) -> list[dict[str, Any]]: ...
    def get_max_metrics_range(self, start: str, end: str) -> list[dict[str, Any]]: ...
    def get_all_day_stress(self, cdate: str) -> dict[str, Any]: ...
    def get_activities_by_date(
        self,
        startdate: str,
        enddate: str | None = None,
        activitytype: str | None = None,
        sortorder: str | None = None,
    ) -> list[dict[str, Any]]: ...


@dataclass(frozen=True)
class GarminMetric:
    metric_type: str
    value: float
    unit: str
    date: str
    source_record_id: str


@dataclass(frozen=True)
class GarminActivity:
    activity_type: str
    name: str | None
    duration_seconds: int
    elapsed_duration_seconds: int | None
    distance_meters: float | None
    average_heart_rate: int | None
    max_heart_rate: int | None
    average_speed_mps: float | None
    elevation_gain_meters: float | None
    calories: float | None
    recorded_at_millis: int
    source_record_id: str


class GarminAdapter:
    SOURCE = "GARMIN"

    def __init__(self, client: GarminClient) -> None:
        self.client = client

    def sync(self, start: str, end: str) -> list[GarminMetric]:
        start_date, end_date = _validate_range(start, end)
        metrics: list[GarminMetric] = []
        metrics.extend(self._steps(start, end))
        metrics.extend(self._resting_hr(start, end))
        metrics.extend(self._sleep(start, end, start_date, end_date))
        metrics.extend(self._hrv(start, end, start_date, end_date))
        metrics.extend(self._vo2_max(start, end, start_date, end_date))
        metrics.extend(self._stress(start_date, end_date))
        return sorted(metrics, key=lambda item: (item.date, item.metric_type))

    def sync_activities(self, start: str, end: str) -> list[GarminActivity]:
        _validate_range(start, end)
        rows = self.client.get_activities_by_date(start, end) or []
        activities: list[GarminActivity] = []
        for row in rows:
            activity = _normalize_activity(row)
            # Strength/workout remains Hevy-authoritative for V1. Garmin
            # strength records are deliberately ignored until reconciliation
            # is implemented explicitly.
            if activity is not None and activity.activity_type not in {"STRENGTH", "WORKOUT"}:
                activities.append(activity)
        return sorted(activities, key=lambda item: item.recorded_at_millis, reverse=True)

    def _steps(self, start: str, end: str) -> list[GarminMetric]:
        return _records(self.client.get_daily_steps(start, end), "STEPS", ("totalSteps", "total_steps"), "count", "steps")

    def _resting_hr(self, start: str, end: str) -> list[GarminMetric]:
        return _records(self.client.get_rhr_daily(start, end), "RESTING_HR", ("value", "restingHeartRate", "resting_heart_rate"), "bpm", "rhr")

    def _sleep(self, start: str, end: str, start_date: date, end_date: date) -> list[GarminMetric]:
        output: list[GarminMetric] = []
        for row in self.client.get_sleep_daily(start, end) or []:
            sleep = row.get("dailySleepDTO") or row.get("daily_sleep_dto") or row
            day = _date(row) or _date(sleep) or _find_date(row)
            value = _find_number(sleep, ("sleepTimeSeconds", "sleep_time_seconds"))
            if day and value is not None:
                output.append(GarminMetric("SLEEP", value, "seconds", day, f"sleep:{day}"))
        return self._fill_daily(output, start_date, end_date, "SLEEP", "seconds", "sleep", "get_sleep_data", ("sleepTimeSeconds", "sleep_time_seconds"), nested_key="dailySleepDTO")

    def _hrv(self, start: str, end: str, start_date: date, end_date: date) -> list[GarminMetric]:
        output: list[GarminMetric] = []
        for row in self.client.get_hrv_data_range(start, end) or []:
            summary = row.get("hrvSummary") or row.get("hrv_summary") or row
            day = _date(row) or _date(summary) or _find_date(row)
            value = _find_number(summary, ("lastNightAvg", "last_night_avg"))
            if day and value is not None:
                output.append(GarminMetric("HRV", value, "ms", day, f"hrv:{day}"))
        return self._fill_daily(output, start_date, end_date, "HRV", "ms", "hrv", "get_hrv_data", ("lastNightAvg", "last_night_avg"), nested_key="hrvSummary")

    def _vo2_max(self, start: str, end: str, start_date: date, end_date: date) -> list[GarminMetric]:
        output: list[GarminMetric] = []
        for row in self.client.get_max_metrics_range(start, end) or []:
            day, value = _extract_vo2(row)
            if day and value is not None:
                output.append(GarminMetric("VO2_MAX", value, "ml/kg/min", day, f"vo2max:{day}"))

        existing = {item.date for item in output}
        getter = getattr(self.client, "get_max_metrics", None)
        current = start_date
        while getter is not None and current <= end_date:
            day = current.isoformat()
            if day not in existing:
                try:
                    response = getter(day) or {}
                    value = _extract_vo2_value(response)
                    if value is not None:
                        output.append(GarminMetric("VO2_MAX", value, "ml/kg/min", day, f"vo2max:{day}"))
                        existing.add(day)
                except Exception:
                    pass
            current += timedelta(days=1)

        getter = getattr(self.client, "get_training_status", None)
        current = start_date
        while getter is not None and current <= end_date:
            day = current.isoformat()
            if day not in existing:
                try:
                    response = getter(day) or {}
                    value = _find_number(
                        response.get("mostRecentVO2Max", {}).get("generic", {}),
                        ("vo2MaxValue", "vo2MaxPreciseValue", "vo2_max_value", "vo2_max_precise_value"),
                    )
                    if value is not None:
                        output.append(GarminMetric("VO2_MAX", value, "ml/kg/min", day, f"vo2max:{day}"))
                        existing.add(day)
                except Exception:
                    pass
            current += timedelta(days=1)
        return output

    def _fill_daily(self, output: list[GarminMetric], start: date, end: date, metric_type: str, unit: str, prefix: str, method_name: str, value_keys: tuple[str, ...], nested_key: str | None = None) -> list[GarminMetric]:
        existing = {item.date for item in output}
        getter = getattr(self.client, method_name, None)
        if getter is None:
            return output
        current = start
        while current <= end:
            day = current.isoformat()
            if day not in existing:
                try:
                    response = getter(day) or {}
                    target = response.get(nested_key) if nested_key else response
                    target = target or response
                    value = _find_number(target, value_keys)
                    if value is not None:
                        output.append(GarminMetric(metric_type, value, unit, day, f"{prefix}:{day}"))
                except Exception:
                    pass
            current += timedelta(days=1)
        return output

    def _stress(self, start: date, end: date) -> list[GarminMetric]:
        output: list[GarminMetric] = []
        current = start
        while current <= end:
            day = current.isoformat()
            value = _stress_average(self.client.get_all_day_stress(day) or {})
            if value is not None and value >= 0:
                output.append(GarminMetric("STRESS", value, "score", day, f"stress:{day}"))
            current += timedelta(days=1)
        return output


def _normalize_activity(row: Any) -> GarminActivity | None:
    if not isinstance(row, dict):
        return None
    activity_id = _number(row, "activityId", "activity_id")
    if activity_id is None:
        return None
    type_data = row.get("activityType") or row.get("activity_type") or {}
    raw_type = type_data.get("typeKey") if isinstance(type_data, dict) else type_data
    activity_type = _map_activity_type(str(raw_type or row.get("activityTypeKey") or "other"))
    # Garmin's local timestamp is the canonical display time for HealthOS.
    # Prefer it over startTimeGMT so Garmin and Strava exports line up in the
    # user's local timezone during cross-source deduplication.
    recorded_at = _parse_activity_time(
        row.get("startTimeLocal")
        or row.get("start_time_local")
        or row.get("startTimeGMT")
        or row.get("start_time_gmt")
    )
    if recorded_at is None:
        return None
    duration = _number(row, "duration")
    elapsed = _number(row, "elapsedDuration", "elapsed_duration")
    if duration is None and elapsed is None:
        return None
    return GarminActivity(
        activity_type=activity_type,
        name=_string(row, "activityName", "activity_name"),
        duration_seconds=int(duration or elapsed or 0),
        elapsed_duration_seconds=int(elapsed) if elapsed is not None else None,
        distance_meters=_number(row, "distance"),
        average_heart_rate=_int_number(row, "averageHR", "average_hr"),
        max_heart_rate=_int_number(row, "maxHR", "max_hr"),
        average_speed_mps=_number(row, "averageSpeed", "average_speed"),
        elevation_gain_meters=_number(row, "elevationGain", "elevation_gain"),
        calories=_number(row, "calories"),
        recorded_at_millis=recorded_at,
        source_record_id=str(int(activity_id)),
    )


def _map_activity_type(value: str) -> str:
    key = value.strip().lower().replace("-", "_").replace(" ", "_")
    mapping = {
        "running": "RUN", "trail_running": "RUN", "treadmill_running": "RUN",
        "cycling": "RIDE", "road_biking": "RIDE", "indoor_cycling": "RIDE", "virtual_ride": "RIDE",
        "walking": "WALK", "hiking": "HIKE", "swimming": "SWIM",
        "soccer": "SOCCER", "rock_climbing": "ROCK_CLIMB", "canoeing": "CANOE",
        "strength_training": "STRENGTH", "fitness_equipment": "WORKOUT", "workout": "WORKOUT",
    }
    return mapping.get(key, "OTHER")


def _parse_activity_time(value: Any) -> int | None:
    if not isinstance(value, str) or not value.strip():
        return None
    raw = value.strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(raw)
    except ValueError:
        for pattern in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S"):
            try:
                parsed = datetime.strptime(value.strip(), pattern)
                break
            except ValueError:
                parsed = None
        if parsed is None:
            return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp() * 1000)


def _string(row: dict[str, Any], *keys: str) -> str | None:
    for key in keys:
        value = row.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return None


def _int_number(row: dict[str, Any], *keys: str) -> int | None:
    value = _number(row, *keys)
    return int(value) if value is not None else None


def _validate_range(start: str, end: str) -> tuple[date, date]:
    try:
        first = date.fromisoformat(start)
        last = date.fromisoformat(end)
    except ValueError as exc:
        raise ValueError("dates must use YYYY-MM-DD") from exc
    if first > last:
        raise ValueError("start date cannot be after end date")
    return first, last


def _date(row: Any) -> str | None:
    if not isinstance(row, dict):
        return None
    for key in ("calendarDate", "calendar_date", "date"):
        value = row.get(key)
        if isinstance(value, str) and value:
            return value[:10]
    return None


def _find_date(value: Any) -> str | None:
    if isinstance(value, dict):
        found = _date(value)
        if found:
            return found
        for child in value.values():
            found = _find_date(child)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = _find_date(child)
            if found:
                return found
    return None


def _number(row: Any, *keys: str) -> float | None:
    if not isinstance(row, dict):
        return None
    for key in keys:
        value = row.get(key)
        if isinstance(value, bool):
            continue
        if isinstance(value, (int, float)):
            return float(value)
        if isinstance(value, str):
            try:
                return float(value.strip())
            except ValueError:
                continue
    return None


def _find_number(value: Any, keys: tuple[str, ...]) -> float | None:
    if isinstance(value, dict):
        found = _number(value, *keys)
        if found is not None:
            return found
        for child in value.values():
            found = _find_number(child, keys)
            if found is not None:
                return found
    elif isinstance(value, list):
        for child in value:
            found = _find_number(child, keys)
            if found is not None:
                return found
    return None


def _extract_vo2(row: Any) -> tuple[str | None, float | None]:
    if not isinstance(row, dict):
        return None, None
    generic = row.get("generic") or {}
    day = _date(generic) or _date(row)
    value = _find_number(generic, ("vo2MaxValue", "vo2MaxPreciseValue", "vo2_max_value", "vo2_max_precise_value"))
    if value is None:
        value = _find_number(row, ("vo2MaxValue", "vo2MaxPreciseValue", "vo2_max_value", "vo2_max_precise_value"))
    return day, value


def _extract_vo2_value(response: Any) -> float | None:
    if not isinstance(response, dict):
        return None
    generic = response.get("generic") or {}
    value = _find_number(generic, ("vo2MaxValue", "vo2MaxPreciseValue", "vo2_max_value", "vo2_max_precise_value"))
    if value is not None:
        return value
    return _find_number(response, ("vo2MaxValue", "vo2MaxPreciseValue", "vo2_max_value", "vo2_max_precise_value"))


def _records(rows: list[dict[str, Any]] | None, metric_type: str, value_keys: tuple[str, ...], unit: str, id_prefix: str) -> list[GarminMetric]:
    output: list[GarminMetric] = []
    for row in rows or []:
        day = _date(row) or _find_date(row)
        value = _find_number(row, value_keys)
        if day and value is not None:
            output.append(GarminMetric(metric_type, value, unit, day, f"{id_prefix}:{day}"))
    return output


def _stress_average(response: Any) -> float | None:
    keys = ("averageStressLevel", "average_stress_level", "avgStressLevel", "avg_stress_level", "averageStress", "average_stress")
    return _find_number(response, keys)
