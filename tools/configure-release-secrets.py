"""We install release secrets through an authenticated GitHub CLI without printing them."""

import base64
import json
import subprocess
import sys
from pathlib import Path

REPOSITORY = "J-CE/SeaFrogs-SF-PH-01-Camera"


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python tools/configure-release-secrets.py /private/release-signing.json")
    configuration_path = Path(sys.argv[1]).resolve()
    configuration = json.loads(configuration_path.read_text(encoding="utf-8"))
    keystore_path = Path(configuration["SEAFROGS_RELEASE_KEYSTORE"])
    if not keystore_path.is_absolute():
        keystore_path = configuration_path.parent / keystore_path
    secrets = {
        "SEAFROGS_RELEASE_KEYSTORE_BASE64": base64.b64encode(keystore_path.read_bytes()).decode("ascii"),
        **{name: configuration[name] for name in (
            "SEAFROGS_RELEASE_STORE_PASSWORD", "SEAFROGS_RELEASE_KEY_ALIAS", "SEAFROGS_RELEASE_KEY_PASSWORD"
        )},
    }
    if not all(secrets.values()):
        raise SystemExit("Incomplete release signing configuration")
    for name, value in secrets.items():
        subprocess.run(["gh", "secret", "set", name, "--repo", REPOSITORY],
                       input=value, text=True, check=True, stdout=subprocess.DEVNULL)
    print("Release secrets configured.")


if __name__ == "__main__":
    main()
