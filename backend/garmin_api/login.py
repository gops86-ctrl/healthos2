"""One-time interactive Garmin login that stores encrypted tokens in Upstash.

Run this on your own computer, not on the Android phone and not by sending
Garmin credentials to HealthOS. The resulting token data is encrypted before
it is written to Upstash Redis.
"""

from __future__ import annotations

import getpass
import os

from garminconnect import Garmin

from garmin_api.token_store import (
    create_temporary_token_store,
    read_temporary_token_store,
    save_token_json,
)


def main() -> None:
    if not (os.getenv("UPSTASH_REDIS_REST_URL") and os.getenv("UPSTASH_REDIS_REST_TOKEN")):
        raise SystemExit("Set UPSTASH_REDIS_REST_URL and UPSTASH_REDIS_REST_TOKEN first.")
    if not os.getenv("GARMIN_TOKEN_ENCRYPTION_KEY"):
        raise SystemExit("Set GARMIN_TOKEN_ENCRYPTION_KEY first.")

    email = os.getenv("GARMIN_EMAIL") or input("Garmin email: ").strip()
    password = os.getenv("GARMIN_PASSWORD") or getpass.getpass("Garmin password: ")

    temporary_store = create_temporary_token_store(None)
    try:
        client = Garmin(email, password, prompt_mfa=lambda: input("Garmin MFA code: "))
        client.login(temporary_store.name)
        save_token_json(read_temporary_token_store(temporary_store.name))
    finally:
        temporary_store.cleanup()

    print("Garmin login successful. Encrypted token store saved to Upstash Redis.")


if __name__ == "__main__":
    main()
