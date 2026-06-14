import math
import struct
import time
from typing import Any


def f32(buf: bytes, off: int) -> float | None:
    try:
        v = struct.unpack_from("<f", buf, off)[0]
        if math.isfinite(v):
            return float(v)
    except Exception:
        pass
    return None


def i32(buf: bytes, off: int) -> int | None:
    try:
        return int(struct.unpack_from("<i", buf, off)[0])
    except Exception:
        return None


def u32(buf: bytes, off: int) -> int | None:
    try:
        return int(struct.unpack_from("<I", buf, off)[0])
    except Exception:
        return None


def ms_to_time(ms: int | None) -> str:
    if ms is None or ms <= 0 or ms > 24 * 60 * 60 * 1000:
        return "--:--.---"
    minutes = ms // 60000
    seconds = (ms % 60000) // 1000
    millis = ms % 1000
    return f"{minutes:02d}:{seconds:02d}.{millis:03d}"


def clamp_percent(v: float | None) -> float:
    if v is None:
        return 0.0
    if 0 <= v <= 1:
        return round(v * 100, 1)
    if 0 <= v <= 100:
        return round(v, 1)
    return 0.0


class GT7Parser:
    def parse(self, plain: bytes, source_ip: str | None = None) -> dict[str, Any]:
        now = time.time()
        magic = plain[0:4].decode("latin1", errors="replace") if len(plain) >= 4 else ""
        packet_id = u32(plain, 0x04)

        vx, vy, vz = f32(plain, 0x1C), f32(plain, 0x20), f32(plain, 0x24)
        speed_kmh = None
        if vx is not None and vy is not None and vz is not None:
            speed_kmh = math.sqrt(vx * vx + vy * vy + vz * vz) * 3.6
            if speed_kmh < 0 or speed_kmh > 700:
                speed_kmh = None

        rpm = f32(plain, 0x3C)
        rpm_max = f32(plain, 0x40)
        fuel = f32(plain, 0x44)
        fuel_capacity = f32(plain, 0x48)
        current_lap_ms = i32(plain, 0x74)
        best_lap_ms = i32(plain, 0x78)
        last_lap_ms = i32(plain, 0x7C)
        current_lap = i32(plain, 0x70)
        throttle = clamp_percent(f32(plain, 0x91))
        brake = clamp_percent(f32(plain, 0x92))
        gear_raw = i32(plain, 0x90)
        gear = "N"
        if gear_raw is not None:
            g = gear_raw & 0x0F
            gear = "R" if g == 15 else ("N" if g == 0 else str(g))

        fuel_percent = None
        if fuel is not None and fuel_capacity and fuel_capacity > 0:
            fuel_percent = max(0, min(100, fuel / fuel_capacity * 100))

        raw_probe = {}
        for off in range(0, min(len(plain), 180), 4):
            val = f32(plain, off)
            if val is not None and -100000 < val < 100000:
                raw_probe[f"0x{off:02X}"] = round(val, 4)

        connected = magic == "G7S0" or len(plain) >= 296
        return {
            "connected": connected,
            "timestamp": now,
            "source_ip": source_ip,
            "packet_bytes": len(plain),
            "magic": magic,
            "packet_id": packet_id,
            "speed_kmh": round(speed_kmh or 0, 1),
            "rpm": round(rpm or 0),
            "rpm_max": round(rpm_max or 0),
            "gear": gear,
            "throttle": throttle,
            "brake": brake,
            "fuel_liters": round(fuel or 0, 2),
            "fuel_capacity": round(fuel_capacity or 0, 2),
            "fuel_percent": round(fuel_percent or 0, 1),
            "current_lap": current_lap if current_lap and current_lap > 0 else 0,
            "current_lap_time": ms_to_time(current_lap_ms),
            "best_lap_time": ms_to_time(best_lap_ms),
            "last_lap_time": ms_to_time(last_lap_ms),
            "raw_probe": raw_probe,
        }
