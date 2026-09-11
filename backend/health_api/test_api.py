from fastapi.testclient import TestClient

from health_api import main

client = TestClient(main.app)


def test_health_without_storage() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "storage": "unconfigured"}


def test_snapshot_contract(monkeypatch) -> None:
    saved = {}

    monkeypatch.setattr(main, "storage_configured", lambda: True)
    monkeypatch.setattr(main, "save_snapshot", lambda snapshot: saved.update(snapshot))
    monkeypatch.setattr(main, "load_snapshot", lambda: saved)

    response = client.post(
        "/health/snapshot",
        json={
            "metrics": [{"metricType": "RESTING_HR", "value": 50, "recordedAtMillis": 100}],
            "activities": [{"activityType": "RUN", "durationSeconds": 1800, "recordedAtMillis": 200}],
            "workouts": [],
            "nutrition": [],
            "body_measurements": [],
            "labs": [],
            "profile": {"name": "Test"},
        },
    )
    assert response.status_code == 200
    assert response.json()["status"] == "stored"
    assert response.json()["counts"]["activities"] == 1
    assert response.json()["profile_present"] is True

    response = client.get("/health/activities", params={"start_millis": 150})
    assert response.status_code == 200
    assert len(response.json()) == 1

    response = client.get("/health/metrics", params={"metric_type": "resting_hr"})
    assert response.status_code == 200
    assert response.json()[0]["value"] == 50

    response = client.get("/health/profile")
    assert response.status_code == 200
    assert response.json() == {"name": "Test"}


def test_metric_history_preserves_all_records_and_filters_by_range(monkeypatch) -> None:
    saved = {}

    monkeypatch.setattr(main, "storage_configured", lambda: True)
    monkeypatch.setattr(main, "save_snapshot", lambda snapshot: saved.update(snapshot))
    monkeypatch.setattr(main, "load_snapshot", lambda: saved)

    metrics = [
        {"metricType": "RESTING_HR", "value": 52, "recordedAtMillis": 1000},
        {"metricType": "RESTING_HR", "value": 50, "recordedAtMillis": 2000},
        {"metricType": "RESTING_HR", "value": 48, "recordedAtMillis": 3000},
        {"metricType": "VO2_MAX", "value": 51, "recordedAtMillis": 2500},
    ]
    response = client.post(
        "/health/snapshot",
        json={"metrics": metrics},
        headers={"X-HealthOS-API-Key": ""},
    )
    assert response.status_code == 200
    assert response.json()["counts"]["metrics"] == 4

    response = client.get("/health/metrics", params={"metric_type": "RESTING_HR"})
    assert response.status_code == 200
    assert response.json() == metrics[:3]

    response = client.get(
        "/health/metrics",
        params={"metric_type": "RESTING_HR", "start_millis": 2000, "end_millis": 3000},
    )
    assert response.status_code == 200
    assert response.json() == metrics[1:3]

    response = client.get("/health/metrics", params={"metric_type": "VO2_MAX"})
    assert response.status_code == 200
    assert response.json() == [metrics[3]]
