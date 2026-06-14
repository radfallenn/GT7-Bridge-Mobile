import asyncio
import os
import socket
import time
from .gt7_crypto import decrypt_packet
from .parser import GT7Parser
from .state import TelemetryState

class GT7Receiver:
    def __init__(self, state: TelemetryState) -> None:
        self.state = state
        self.parser = GT7Parser()
        self.ps5_ip = os.getenv("PS5_IP", "").strip()
        self.listen_port = int(os.getenv("GT7_UDP_PORT", "33740"))
        self.heartbeat_port = int(os.getenv("GT7_PS5_PORT", "33739"))
        self.heartbeat_text = os.getenv("GT7_HEARTBEAT", "A").encode("ascii")
        self.running = False

    async def start(self) -> None:
        self.running = True
        await asyncio.gather(self._heartbeat_loop(), self._listen_loop())

    async def _heartbeat_loop(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        while self.running:
            if self.ps5_ip:
                try:
                    sock.sendto(self.heartbeat_text, (self.ps5_ip, self.heartbeat_port))
                except Exception:
                    pass
            await asyncio.sleep(1.0)

    async def _listen_loop(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("0.0.0.0", self.listen_port))
        sock.setblocking(False)
        loop = asyncio.get_running_loop()
        while self.running:
            try:
                packet, addr = await loop.sock_recvfrom(sock, 4096)
                try:
                    plain = decrypt_packet(packet)
                except Exception:
                    plain = packet
                data = self.parser.parse(plain, addr[0] if addr else None)
                data["server_time"] = time.strftime("%H:%M:%S")
                self.state.update(data)
            except Exception:
                await asyncio.sleep(0.1)
