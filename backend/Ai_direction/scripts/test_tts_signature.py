"""Debug Tencent Cloud TC3 signature by dumping raw HTTP request."""
import hashlib
import hmac
import json
import time
import uuid
from datetime import datetime, timezone
import http.client
import socket

SECRET_ID = "YOUR_TENCENT_SECRET_ID"
SECRET_KEY = "YOUR_TENCENT_SECRET_KEY"
REGION = "ap-guangzhou"
ALGORITHM = "TC3-HMAC-SHA256"


def sign(key, msg):
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def sha256_hex(s):
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def build_request(service, host, action, version, payload):
    timestamp = int(time.time())
    date = datetime.fromtimestamp(timestamp, tz=timezone.utc).strftime("%Y-%m-%d")
    ts_iso = datetime.fromtimestamp(timestamp, tz=timezone.utc).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )

    ct = "application/json; charset=utf-8"
    canonical_headers = f"content-type:{ct}\nhost:{host}\n"
    signed_headers = "content-type;host"
    hashed_payload = sha256_hex(payload)

    canonical_request = (
        "POST\n/\n\n"
        + canonical_headers + "\n"
        + signed_headers + "\n"
        + hashed_payload
    )

    credential_scope = f"{date}/{service}/tc3_request"
    hashed_canonical = sha256_hex(canonical_request)
    string_to_sign = (
        f"{ALGORITHM}\n{timestamp}\n{credential_scope}\n{hashed_canonical}"
    )

    k_date = sign(("TC3" + SECRET_KEY).encode("utf-8"), date)
    k_service = sign(k_date, service)
    k_signing = sign(k_service, "tc3_request")
    signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

    authorization = (
        f"{ALGORITHM} "
        f"Credential={SECRET_ID}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, "
        f"Signature={signature}"
    )

    global REGION
    req_body = (
        f"POST / HTTP/1.1\r\n"
        f"Host: {host}\r\n"
        f"Content-Type: {ct}\r\n"
        f"X-TC-Action: {action}\r\n"
        f"X-TC-Version: {version}\r\n"
        f"X-TC-Timestamp: {timestamp}\r\n"
        f"X-TC-Region: {REGION}\r\n"
        f"Authorization: {authorization}\r\n"
        f"Content-Length: {len(payload.encode('utf-8'))}\r\n"
        f"Connection: close\r\n"
        f"\r\n"
        f"{payload}"
    )

    print(f"\n=== {action} ===")
    print(f"CanonicalRequest:\n{canonical_request}")
    print(f"CanonicalRequest hash: {hashed_canonical}")
    print(f"StringToSign:\n{string_to_sign}")
    print(f"kDate: {k_date.hex()}")
    print(f"kService: {k_service.hex()}")
    print(f"kSigning: {k_signing.hex()}")
    print(f"Signature: {signature}")
    print(f"\n=== Raw HTTP Request ===")
    print(req_body)
    print(f"=== End Raw HTTP ===")

    return req_body.encode("utf-8")


def send_raw(host, raw_bytes):
    """Send raw HTTP request via socket."""
    print(f"\nConnecting to {host}:443...")
    import ssl
    ctx = ssl.create_default_context()
    sock = socket.create_connection((host, 443), timeout=15)
    ssock = ctx.wrap_socket(sock, server_hostname=host)
    ssock.sendall(raw_bytes)

    response = b""
    while True:
        try:
            data = ssock.recv(4096)
            if not data:
                break
            response += data
        except socket.timeout:
            break

    ssock.close()
    resp_str = response.decode("utf-8", errors="replace")
    print(f"\n=== Response ===")
    print(resp_str)
    return resp_str


if __name__ == "__main__":
    host = "tts.tencentcloudapi.com"
    payload = json.dumps({
        "Text": "你好",
        "SessionId": str(uuid.uuid4()),
        "VoiceType": 101001,
        "PrimaryLanguage": 1,
        "Speed": 0.0,
        "Codec": "mp3",
        "SampleRate": 16000,
        "Volume": 5.0,
    }, ensure_ascii=False)

    raw = build_request("tts", host, "TextToVoice", "2019-08-23", payload)
    send_raw(host, raw)
