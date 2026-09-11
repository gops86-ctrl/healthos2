# HealthOS Garmin API

This service keeps Garmin authentication off the Android device. The Android app calls `/garmin/sync`; this service uses the existing `garmin_adapter` to normalize Garmin Connect data.

## Free deployment architecture

Use:

```text
HealthOS Android
      |
      | HTTPS
      v
Render Free Web Service ($0)
      |
      | Upstash REST
      v
Upstash Redis Free ($0)
      |
      v
Garmin Connect
```

Render Free has an ephemeral filesystem, so Garmin tokens are **not** stored on the Render disk. They are encrypted with Fernet and stored in Upstash Redis. Upstash's Python SDK supports `Redis.from_env()` with `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN`. See the official Upstash Python setup docs: https://upstash.com/docs/redis/sdks/py/gettingstarted

## One-time Garmin authentication

Run this on your own computer. **Do not send your Garmin password or MFA code to HealthOS or put them in GitHub.**

First create an Upstash Redis database and copy its REST URL/token.

Generate one encryption key locally:

```bash
python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
```

Set these environment variables on your computer:

```bash
export UPSTASH_REDIS_REST_URL='your-upstash-rest-url'
export UPSTASH_REDIS_REST_TOKEN='your-upstash-rest-token'
export GARMIN_TOKEN_ENCRYPTION_KEY='the-generated-fernet-key'
```

Then install dependencies and run:

```bash
python -m pip install -r backend/garmin_api/requirements.txt
PYTHONPATH=backend python backend/garmin_api/login.py
```

The script asks for your Garmin email, password, and MFA code locally. It logs in to Garmin and stores only the encrypted token store in Upstash. The refresh token is sensitive and should be treated like a password.

## Render environment variables

Add these to the Render service:

```text
UPSTASH_REDIS_REST_URL=<from Upstash>
UPSTASH_REDIS_REST_TOKEN=<from Upstash>
GARMIN_TOKEN_ENCRYPTION_KEY=<same key used by the local login script>
HEALTHOS_API_KEY=<long random API key>
```

`GARMIN_TOKEN_REDIS_KEY` is optional; the default is `healthos:garmin:tokens`.

## Run the API locally

```bash
PYTHONPATH=backend uvicorn garmin_api.main:app --host 0.0.0.0 --port 8000
```

## Render settings

- Language: **Docker**
- Branch: **main**
- Root Directory: leave blank
- Dockerfile Path: `backend/garmin_api/Dockerfile`
- The Dockerfile uses Render's `$PORT` automatically.

## Android configuration

Set `GARMIN_API_BASE_URL` in `app/build.gradle.kts` to the HTTPS URL of this service. Keep it blank when Garmin sync is not configured. HealthOS then attempts a 7-day Garmin sync when the app starts and upserts the returned metrics using stable Garmin source IDs.

The six V1 metrics are imported: VO₂ max, resting HR, HRV, sleep, stress, and steps.

## Security note

`HEALTHOS_API_KEY` is a basic access gate, not a complete mobile-app authentication system. A static key embedded in an APK can eventually be extracted. It is acceptable for the current single-user V1/private deployment, but a production multi-user release should use real user authentication.
