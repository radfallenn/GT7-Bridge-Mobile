#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
sudo apt update
sudo apt install -y python3 python3-venv python3-pip
python3 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
echo "Instalado. Rode: PS5_IP=IP_DO_PS5 ./scripts/run.sh"
