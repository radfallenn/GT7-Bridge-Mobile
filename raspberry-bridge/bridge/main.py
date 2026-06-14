import asyncio
import os
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from .receiver import GT7Receiver
from .state import TelemetryState

app = FastAPI(title="GT7 rAd Telemetry Bridge", version="0.1.0")
state = TelemetryState()
receiver = GT7Receiver(state)

app.mount("/static", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "app")), name="static")

@app.on_event("startup")
async def startup() -> None:
    asyncio.create_task(receiver.start())

@app.get("/", response_class=HTMLResponse)
async def index() -> str:
    with open(os.path.join(os.path.dirname(__file__), "app", "index.html"), "r", encoding="utf-8") as f:
        return f.read()

@app.get("/api/health")
async def health() -> dict:
    snap = state.snapshot()
    return {
        "ok": True,
        "connected": snap.get("connected", False),
        "packet_count": state.packet_count,
        "ps5_ip": receiver.ps5_ip or None,
        "listen_port": receiver.listen_port,
        "heartbeat_port": receiver.heartbeat_port,
    }

@app.get("/api/config")
async def config() -> dict:
    return {
        "ps5_ip": receiver.ps5_ip or None,
        "udp_listen_port": receiver.listen_port,
        "http_port": int(os.getenv("GT7_HTTP_PORT", "8787")),
    }

@app.get("/api/live")
async def live() -> dict:
    return state.snapshot()

@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket) -> None:
    await ws.accept()
    q = state.subscribe()
    try:
        await ws.send_json(state.snapshot())
        while True:
            data = await q.get()
            await ws.send_json(data)
    except WebSocketDisconnect:
        pass
    finally:
        state.unsubscribe(q)
