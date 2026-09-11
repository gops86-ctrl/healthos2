from fastapi.testclient import TestClient

import main


class FakeMetric:
    metric_type = "RESTING_HR"
    value = 42.0
    unit = "bpm"
    date = "2026-09-07"
    source_record_id = "rhr:2026-09-07"


class FakeActivity:
    activity_type = "RUN"
    name = "Morning Run"
    duration_seconds = 3120
    elapsed_duration_seconds = 3180
    distance_meters = 8420.0
    average_heart_rate = 151
    max_heart_rate = 177
    average_speed_mps = 2.7
    elevation_gain_meters = 82.0
    calories = 610.0
    recorded_at_millis = 1788744600000
    source_record_id = "123456"


class FakeClient:
    pass


class FakeTemporaryStore:
    name = "/tmp/fake-garmin"

    def cleanup(self):
        pass


def test_health_endpoint():
    client = TestClient(main.app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_sync_returns_normalized_metrics(monkeypatch):
    monkeypatch.setattr(main, "_client_from_redis", lambda: (FakeClient(), FakeTemporaryStore()))
    monkeypatch.setattr(main, "_persist_refreshed_tokens", lambda store: None)
    monkeypatch.setattr(main.GarminAdapter, "sync", lambda self, start, end: [FakeMetric()])
    client = TestClient(main.app)
    response = client.post("/garmin/sync", json={"start": "2026-09-07", "end": "2026-09-07"})
    assert response.status_code == 200
    assert response.json()["metrics"] == [
        {
            "type": "RESTING_HR",
            "value": 42.0,
            "unit": "bpm",
            "date": "2026-09-07",
            "sourceRecordId": "garmin:rhr:2026-09-07",
        }
    ]


def test_activity_sync_returns_normalized_activities(monkeypatch):
    monkeypatch.setattr(main, "_client_from_redis", lambda: (FakeClient(), FakeTemporaryStore()))
    monkeypatch.setattr(main, "_persist_refreshed_tokens", lambda store: None)
    monkeypatch.setattr(main.GarminAdapter, "sync_activities", lambda self, start, end: [FakeActivity()])
    client = TestClient(main.app)
    response = client.post("/garmin/activities", json={"start": "2026-09-07", "end": "2026-09-07"})
    assert response.status_code == 200
    assert response.json()["activities"] == [
        {
            "activityType": "RUN",
            "name": "Morning Run",
            "durationSeconds": 3120,
            "elapsedDurationSeconds": 3180,
            "distanceMeters": 8420.0,
            "averageHeartRate": 151,
            "maxHeartRate": 177,
            "averageSpeedMps": 2.7,
            "elevationGainMeters": 82.0,
            "calories": 610.0,
            "routePoints": None,
            "recordedAtMillis": 1788744600000,
            "sourceRecordId": "garmin:123456",
        }
    ]


def test_tcx_route_points_are_compacted():
    payload = b'''<?xml version="1.0"?><TrainingCenterDatabase><Activities><Activity><Lap><Track><Trackpoint><Position><LatitudeDegrees>17.40</LatitudeDegrees><LongitudeDegrees>78.48</LongitudeDegrees></Position></Trackpoint><Trackpoint><Position><LatitudeDegrees>17.41</LatitudeDegrees><LongitudeDegrees>78.49</LongitudeDegrees></Position></Trackpoint></Track></Lap></Activity></Activities></TrainingCenterDatabase>'''
    assert main._route_points_from_activity_file(payload) == "17.4,78.48;17.41,78.49"
