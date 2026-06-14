from Crypto.Cipher import Salsa20

KEY = b"Simulator Interface Packet GT7 ver 0.0"


def decrypt_packet(packet: bytes) -> bytes:
    if len(packet) < 0x44:
        return packet
    seed = int.from_bytes(packet[0x40:0x44], "little", signed=False)
    iv = seed ^ 0xDEADBEAF
    nonce = iv.to_bytes(4, "little") + seed.to_bytes(4, "little")
    cipher = Salsa20.new(key=KEY, nonce=nonce)
    return cipher.decrypt(packet)
