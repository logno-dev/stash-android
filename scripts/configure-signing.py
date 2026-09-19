#!/usr/bin/env python3
"""Create/reuse a local signing backup and configure the repository's Actions secrets."""

import base64
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BACKUP = ROOT / ".signing"
KEYSTORE = BACKUP / "stash-release.jks"
CREDENTIALS = BACKUP / "credentials.json"


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "logno-dev/stash-android"
    subprocess.run(["gh", "auth", "status"], check=True)
    os.umask(0o077)
    BACKUP.mkdir(mode=0o700, exist_ok=True)
    if KEYSTORE.exists() != CREDENTIALS.exists():
        raise SystemExit(f"Incomplete signing backup in {BACKUP}; recover it before continuing.")
    if KEYSTORE.exists():
        credentials = json.loads(CREDENTIALS.read_text())
    else:
        password = secrets.token_urlsafe(48)
        credentials = {"storePassword": password, "keyPassword": password, "keyAlias": "stash"}
        env = {**os.environ, "STASH_SIGNING_PASSWORD": password}
        subprocess.run([
            "keytool", "-genkeypair", "-keystore", str(KEYSTORE), "-storetype", "JKS",
            "-alias", credentials["keyAlias"], "-keyalg", "RSA", "-keysize", "4096",
            "-validity", "10000", "-dname", "CN=Stash Android, O=logno-dev",
            "-storepass:env", "STASH_SIGNING_PASSWORD", "-keypass:env", "STASH_SIGNING_PASSWORD",
        ], check=True, env=env)
        CREDENTIALS.write_text(json.dumps(credentials, indent=2) + "\n")
    values = {
        "ANDROID_KEYSTORE_BASE64": base64.b64encode(KEYSTORE.read_bytes()).decode(),
        "ANDROID_KEYSTORE_PASSWORD": credentials["storePassword"],
        "ANDROID_KEY_ALIAS": credentials["keyAlias"],
        "ANDROID_KEY_PASSWORD": credentials["keyPassword"],
    }
    for name, value in values.items():
        subprocess.run(["gh", "secret", "set", name, "--repo", repo], input=value, text=True, check=True)
    print(f"Signing secrets configured for {repo}.")
    print(f"Back up {BACKUP} securely; it contains the release key and its passwords.")


if __name__ == "__main__":
    main()
