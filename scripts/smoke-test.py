#!/usr/bin/env python3
"""Exercise the real Mac TCP/HTTP servers with event injection disabled."""
import hashlib
import json
import os
import pathlib
import queue
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "dist/AirMouse.app/Contents/MacOS/AirMouse"
TOKEN = "smoke-test-install-token"
CODE = "ABCDEF123456"
for port in (47821, 47822):
    with socket.socket() as probe:
        if probe.connect_ex(("127.0.0.1", port)) == 0:
            raise SystemExit(f"Port {port} is already in use. Quit AirMouse before running smoke tests.")
env = dict(os.environ, AIR_MOUSE_TEST_CODE=CODE, AIR_MOUSE_TEST_TOKEN=TOKEN)
lines = queue.Queue()
process = subprocess.Popen([str(APP), "--diagnostic-server"], stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT, text=True, env=env)
transcript = []


def collect():
    for line in process.stdout:
        transcript.append(line)
        lines.put(line.strip())


threading.Thread(target=collect, daemon=True).start()


def wait_for(expected, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            line = lines.get(timeout=max(0.01, deadline-time.monotonic()))
        except queue.Empty:
            break
        if line.startswith(expected):
            return line
    raise AssertionError(f"Did not receive {expected!r}. Output: {''.join(transcript)}")


def connect(code=CODE):
    connection = socket.create_connection(("127.0.0.1", 47821), timeout=2)
    file = connection.makefile("rwb", buffering=0)
    file.write((json.dumps({"type": "hello", "version": 1, "code": code}) + "\n").encode())
    response = json.loads(file.readline())
    return connection, file, response


def send(file, message):
    file.write((json.dumps(message) + "\n").encode())


passed = []
try:
    wait_for("READY")
    c, f, message = connect("WRONG")
    assert message["type"] == "error"
    f.close(); c.close()
    passed.append("wrong pairing code rejected")

    c, f, message = connect()
    assert message["type"] == "welcome" and message["enabled"]
    passed.append("valid pairing accepted")
    other, otherfile, message = connect()
    assert message["type"] == "error" and "already connected" in message["message"]
    otherfile.close(); other.close()
    passed.append("second phone rejected")
    send(f, {"type": "move", "dx": 12.5, "dy": -8})
    send(f, {"type": "click", "button": "left"})
    send(f, {"type": "click", "button": "right"})
    wait_for("EVENT move 12.5 -8.0")
    wait_for("EVENT click left")
    wait_for("EVENT click right")
    passed.append("movement and both click types delivered")
    send(f, {"type": "heartbeat"})
    assert json.loads(f.readline())["type"] == "heartbeat"
    passed.append("heartbeat acknowledged")
    send(f, {"type": "move", "dx": 251, "dy": 0})
    assert f.readline() == b""
    f.close(); c.close()
    passed.append("oversized movement disconnects")

    c, f, message = connect()
    assert message["type"] == "welcome"
    f.write(b"X" * 4097)
    assert f.readline() == b""
    f.close(); c.close()
    passed.append("oversized frame disconnects")

    c = socket.create_connection(("127.0.0.1", 47821), timeout=2)
    f = c.makefile("rwb", buffering=0)
    hello = (json.dumps({"type": "hello", "version": 1, "code": CODE}) + "\n").encode()
    f.write(hello[:13]); f.write(hello[13:])
    assert json.loads(f.readline())["type"] == "welcome"
    send(f, {"type": "bye"})
    assert f.readline() == b""
    f.close(); c.close()
    passed.append("fragmented hello and clean disconnect")

    c, f, message = connect()
    c.settimeout(9)
    assert message["type"] == "welcome" and f.readline() == b""
    f.close(); c.close()
    passed.append("silent phone times out")

    base = "http://127.0.0.1:47822"
    # Disable proxy inheritance so this test remains local.
    http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    page = http.open(base + "/" + TOKEN, timeout=3).read().decode()
    assert "airmouse://connect" in page and CODE in page
    apk = http.open(base + "/" + TOKEN + "/AirMouse-Android.apk", timeout=3).read()
    assert hashlib.sha256(apk).digest() == hashlib.sha256((ROOT / "dist/AirMouse-Android.apk").read_bytes()).digest()
    passed.append("installation page and APK served correctly")
    for path in ("/", "/wrong", "/" + TOKEN + "/../../etc/passwd"):
        try:
            http.open(base + path, timeout=3)
            raise AssertionError("Unexpected unprotected page: " + path)
        except urllib.error.HTTPError as error:
            assert error.code == 404
    passed.append("installation link protected; unknown paths rejected")
    print("\n".join("PASS: " + test for test in passed))
    print(f"\n{len(passed)} integration checks passed; no OS mouse events were injected.")
finally:
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
    (ROOT / "dist/smoke-test.log").write_text("".join(transcript))
