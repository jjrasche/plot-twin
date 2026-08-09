"""Shared paths and GET helper for the capture fetch scripts. GET-only, no auth."""

import json
import pathlib
import urllib.request

DATA_DIR = pathlib.Path(__file__).resolve().parent.parent / "data"
USER_AGENT = "plot-twin-capture/0.1 (jimjrasche@gmail.com)"


def http_get(url: str, timeout: int = 600) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def http_get_json(url: str, timeout: int = 120) -> dict:
    return json.loads(http_get(url, timeout=timeout))


def write_json(path: pathlib.Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=1))
    print(f"wrote {path}")
