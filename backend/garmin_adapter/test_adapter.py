from adapter import GarminAdapter


class FakeGarmin:
    def get_daily_steps(self, start, end):
        return [{"calendarDate": "2026-09-06", "totalSteps": 9842}]

    def get_rhr_daily(self, start, end):
        return [{"calendarDate": "2026-09-06", "value": 42}]

    def get_sleep_daily(self, start, end):
        return [{"dailySleepDTO": {"calendarDate": "2026-09-06", "sleepTimeSeconds": 27120}}]

    def get_sleep_data(self, cdate):
        return {"dailySleepDTO": {"calendarDate": cdate, "sleepTimeSeconds": 27120}}

    def get_hrv_data_range(self, start, end):
        return [{"hrvSummary": {"calendarDate": "2026-09-06", "lastNightAvg": 68}}]

    def get_hrv_data(self, cdate):
        return {"hrvSummary": {"calendarDate": cdate, "lastNightAvg": 68}}

    def get_max_metrics_range(self, start, end):
        return [{"calendarDate": "2026-09-06", "metrics": [{"vo2MaxValue": 52.0}]}]

    def get_max_metrics(self, cdate):
        return {"calendarDate": cdate, "metrics": [{"vo2MaxValue": 52.0}]}

    def get_all_day_stress(self, cdate):
        return {"calendarDate": cdate, "averageStressLevel": 23}

    def get_activities_by_date(self, startdate, enddate=None, activitytype=None, sortorder=None):
        return [
            {
                "activityId": 123456,
                "activityName": "Morning Run",
                "startTimeGMT": "2026-09-06T01:30:00.0",
                "activityType": {"typeKey": "running"},
                "duration": 3120.5,
                "elapsedDuration": 3180.0,
                "distance": 8420.0,
                "averageHR": 151,
                "maxHR": 177,
                "averageSpeed": 2.70,
                "elevationGain": 82.0,
                "calories": 610.0,
            },
            {
                "activityId": 123457,
                "activityName": "Push Day",
                "startTimeGMT": "2026-09-06T13:30:00.0",
                "activityType": {"typeKey": "strength_training"},
                "duration": 3000.0,
                "elapsedDuration": 3100.0,
            },
        ]


def test_sync_normalizes_all_v1_metrics():
    metrics = GarminAdapter(FakeGarmin()).sync("2026-09-06", "2026-09-06")
    assert [(m.metric_type, m.value, m.unit, m.source_record_id) for m in metrics] == [
        ("HRV", 68.0, "ms", "hrv:2026-09-06"),
        ("RESTING_HR", 42.0, "bpm", "rhr:2026-09-06"),
        ("SLEEP", 27120.0, "seconds", "sleep:2026-09-06"),
        ("STEPS", 9842.0, "count", "steps:2026-09-06"),
        ("STRESS", 23.0, "score", "stress:2026-09-06"),
        ("VO2_MAX", 52.0, "ml/kg/min", "vo2max:2026-09-06"),
    ]


def test_sync_activities_imports_non_strength_and_skips_strength():
    activities = GarminAdapter(FakeGarmin()).sync_activities("2026-09-06", "2026-09-06")
    assert len(activities) == 1
    activity = activities[0]
    assert activity.activity_type == "RUN"
    assert activity.name == "Morning Run"
    assert activity.duration_seconds == 3120
    assert activity.elapsed_duration_seconds == 3180
    assert activity.distance_meters == 8420.0
    assert activity.average_heart_rate == 151
    assert activity.max_heart_rate == 177
    assert activity.elevation_gain_meters == 82.0
    assert activity.calories == 610.0
    assert activity.source_record_id == "123456"
    assert activity.recorded_at_millis > 0


def test_activity_prefers_local_timestamp_when_both_are_present():
    class LocalTimeGarmin(FakeGarmin):
        def get_activities_by_date(self, startdate, enddate=None, activitytype=None, sortorder=None):
            return [{
                "activityId": 987654,
                "activityName": "Bengaluru Walking",
                "startTimeGMT": "2026-09-07T11:58:00.0",
                "startTimeLocal": "2026-09-07T17:28:00.0",
                "activityType": {"typeKey": "walking"},
                "duration": 4200.0,
                "distance": 5140.0,
            }]

    activity = GarminAdapter(LocalTimeGarmin()).sync_activities("2026-09-07", "2026-09-07")[0]
    assert activity.recorded_at_millis == 1788802080000


def test_missing_hrv_is_skipped_not_zero():
    class MissingHrv(FakeGarmin):
        def get_hrv_data_range(self, start, end):
            return [{"hrvSummary": {"calendarDate": "2026-09-06", "lastNightAvg": None}}]

        def get_hrv_data(self, cdate):
            return {"hrvSummary": {"calendarDate": cdate, "lastNightAvg": None}}

    metrics = GarminAdapter(MissingHrv()).sync("2026-09-06", "2026-09-06")
    assert not any(m.metric_type == "HRV" for m in metrics)


def test_stress_loops_over_each_day():
    class MultiDayStress(FakeGarmin):
        def __init__(self):
            self.calls = []

        def get_all_day_stress(self, cdate):
            self.calls.append(cdate)
            return {"averageStressLevel": 20}

    client = MultiDayStress()
    metrics = GarminAdapter(client).sync("2026-09-05", "2026-09-07")
    assert client.calls == ["2026-09-05", "2026-09-06", "2026-09-07"]
    assert [m.date for m in metrics if m.metric_type == "STRESS"] == [
        "2026-09-05",
        "2026-09-06",
        "2026-09-07",
    ]


def test_stress_sentinel_is_skipped():
    class NoStress(FakeGarmin):
        def get_all_day_stress(self, cdate):
            return {"calendarDate": cdate, "averageStressLevel": -1}

    metrics = GarminAdapter(NoStress()).sync("2026-09-06", "2026-09-06")
    assert not any(m.metric_type == "STRESS" for m in metrics)


def test_invalid_date_range_fails_fast():
    try:
        GarminAdapter(FakeGarmin()).sync("2026-09-07", "2026-09-06")
    except ValueError as exc:
        assert "start date cannot be after end date" in str(exc)
    else:
        raise AssertionError("expected ValueError")
