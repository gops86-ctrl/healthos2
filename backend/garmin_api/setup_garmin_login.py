"""Interactive one-time Garmin login bootstrap.

This script is intentionally local-only. It asks for the Upstash connection
values and encryption key, installs the backend requirements into a local
virtual environment, and then runs the Garmin login flow. Garmin credentials
are prompted by login.py and are never written to the repository.
"""

from __future__ import annotations

import getpass
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
API_DIR = ROOT / "backend" / "garmin_api"
VENV_DIR = API_DIR / ".venv"
REQUIREMENTS = API_DIR / "requirements.txt"


def run(command: list[str], *, env: dict[str, str] | None = None) -> None:
    subprocess.check_call(command, cwd=ROOT, env=env)


def main() -> None:
    if sys.version_info < (3, 12):
        raise SystemExit("Python 3.12 or newer is required for Garmin Connect.")

    print("HealthOS Garmin one-time setup")
    print("Your Garmin password will be requested securely and is not stored.\n")

    url = os.getenv("UPSTASH_REDIS_REST_URL") or input("Upstash Redis REST URL: ").strip()
    token = os.getenv("UPSTASH_REDIS_REST_TOKEN") or getpass.getpass("Upstash Redis REST token: ")
    encryption_key = os.getenv("GARMIN_TOKEN_ENCRYPTION_KEY") or getpass.getpass(
        "Garmin token encryption key: "
    )

    if not url or not token or not encryption_key:
        raise SystemExit("Upstash URL, Upstash token, and encryption key are required.")

    env = os.environ.copy()
    env.update(
        {
            "UPSTASH_REDIS_REST_URL": url,
            "UPSTASH_REDIS_REST_TOKEN": token,
            "GARMIN_TOKEN_ENCRYPTION_KEY": encryption_key,
            "PYTHONPATH": str(ROOT / "backend")
            + os.pathsep
            + env.get("PYTHONPATH", ""),
        }
    )

    if not VENV_DIR.exists():
        print("\nCreating local Python environment...")
        run([sys.executable, "-m", "venv", str(VENV_DIR)], env=env)

    if os.name == "nt":
        python = VENV_DIR / "Scripts" / "python.exe"
    else:
        python = VENV_DIR / "bin" / "python"

    print("Installing HealthOS Garmin dependencies...")
    run([str(python), "-m", "pip", "install", "--upgrade", "pip"], env=env)
    run([str(python), "-m", "pip", "install", "-r", str(REQUIREMENTS)], env=env)

    print("\nStarting Garmin login.\n")
    run([str(python), str(API_DIR / "login.py")], env=env)


if __name__ == "__main__":
    main()
