#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
. .venv/bin/activate
python -m uvicorn bridge.main:app --host 0.0.0.0 --port 8787
