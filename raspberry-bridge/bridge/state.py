import asyncio
import time
from typing import Any

class TelemetryState:
    def __init__(self) -> None:
        self.latest: dict[str, Any] = {
            "connected": False,
            "timestamp": None,
            "packet_count": 0,
            "message": "Aguardando telemetria do GT7",
        }
        self.packet_count = 0
        self.clients: set[asyncio.Queue] = set()

    def update(self, data: dict[str, Any]) -> None:
        self.packet_count += 1
        data["packet_count"] = self.packet_count
        data["age_ms"] = 0
        self.latest = data
        for q in list(self.clients):
            try:
                q.put_nowait(data)
            except asyncio.QueueFull:
                pass

    def snapshot(self) -> dict[str, Any]:
        data = dict(self.latest)
        ts = data.get("timestamp")
        if ts:
            data["age_ms"] = int((time.time() - float(ts)) * 1000)
            if data["age_ms"] > 1500:
                data["connected"] = False
        return data

    def subscribe(self) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue(maxsize=3)
        self.clients.add(q)
        return q

    def unsubscribe(self, q: asyncio.Queue) -> None:
        self.clients.discard(q)
