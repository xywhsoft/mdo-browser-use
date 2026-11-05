"""Real phone/desktop account-relay checks; credentials only cross an in-memory pipe.

Requires the existing deployment and a user-authorized USB-forward receipt.
No login, permission changes, program replacement or model requests are made.
The Node controller runs here; this does not qualify the Android controller UI.
"""
import argparse
import hashlib
import http.client
import json
from pathlib import Path
import subprocess
import time

import dev


class Endpoint:
    def __init__(self, port, host_port):
        self.port, self.host_port = int(port), int(host_port)
        assert 0 < self.port < 65536 and 0 < self.host_port < 65536

    def request(self, method, path, body=None):
        headers = {"Host": "127.0.0.1:" + str(self.host_port)}
        if body is not None:
            status, prior, _ = self.request("GET", "/api/v1/bootstrap")
            assert status == 200
            headers.update({"Content-Type": "application/json",
                            "Origin": "http://127.0.0.1:" + str(self.host_port)})
            token = prior.get("x-mdo-write-token")
            if token:
                headers["X-Mdo-Write-Token"] = token
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=12)
        try:
            connection.request(method, path, json.dumps(body) if body is not None else None, headers)
            response = connection.getresponse()
            return response.status, {k.lower(): v for k, v in response.getheaders()}, json.loads(response.read())
        finally:
            connection.close()

    def data(self, method, path, body=None, expected=200):
        status, _, result = self.request(method, path, body)
        if status != expected or not result.get("ok"):
            code = result.get("error", {}).get("code", "request_failed")
            raise RuntimeError(f"Endpoint request failed: HTTP {status} {code}")
        return result["data"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phone-state", type=Path, required=True)
    parser.add_argument("--direction", choices=("desktop-to-phone", "phone-to-desktop"), required=True)
    parser.add_argument("--mode", choices=("view", "control"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise RuntimeError("An original authentication receipt is preserved; choose a new receipt path")
    phone = json.loads(args.phone_state.read_text(encoding="utf-8"))["phone"]
    assert phone["package"] == "org.xleaves.mdo.browseruse.dev" and phone["installed_verified"]
    desktop = Endpoint(dev.state()["port"], dev.state()["port"])
    android = Endpoint(phone["local_port"], phone["remote_port"])
    controller, target = (desktop, android) if args.direction == "desktop-to-phone" else (android, desktop)
    account = controller.data("GET", "/api/v1/account")
    other = target.data("GET", "/api/v1/account")
    assert account["state"] == other["state"] == "signed_in"
    assert str(account["profile"]["id"]) == str(other["profile"]["id"])
    local = controller.data("GET", "/api/v1/remote")
    remote = target.data("GET", "/api/v1/remote")
    assert local["device_id"] != remote["device_id"] and remote["allow_remote"]
    assert remote["device_id"] == (phone["device_id"] if args.direction == "desktop-to-phone"
                                    else json.loads(args.phone_state.read_text(encoding="utf-8"))["local"]["device_id"])
    # Enabling remote access assigns an identity before its relay registration
    # completes. Join actual server discovery rather than racing that setup.
    listing = controller.data("POST", "/api/v1/connector/devices", {"action": "refresh"}, expected=202)
    list_job = listing["job"]["id"]
    deadline = time.monotonic() + 20
    while True:
        listing = controller.data("GET", "/api/v1/connector/state")
        assert listing["job"]["id"] == list_job
        if listing["job"]["state"] in ("done", "failed"):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Real device discovery did not finish")
        time.sleep(.2)
    if listing["job"]["state"] != "done":
        args.output.write_text(json.dumps({"phase": "discovery-failed", "passed": False,
            "direction": args.direction, "mode": args.mode, "http_status": listing.get("status"),
            "error_code": listing.get("error"), "real_model_calls": 0}, indent=2) + "\n", encoding="utf-8")
        raise RuntimeError("Real discovery failed; inspect its sanitized receipt")
    targets = [row for row in listing["listing"]["devices"] if row["id"] == remote["device_id"]]
    assert len(targets) == 1 and targets[0]["online"] and targets[0]["allow_remote"] and not targets[0]["revoked"]
    result = controller.data("POST", "/api/v1/connector/devices",
        {"action": "connect", "device_id": remote["device_id"], "mode": args.mode}, expected=202)
    job = result["job"]["id"]
    deadline = time.monotonic() + 20
    while True:
        result = controller.data("GET", "/api/v1/connector/state")
        assert result["job"]["id"] == job
        if result["job"]["state"] in ("done", "failed"):
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Real connector job did not reach a terminal state")
        time.sleep(.2)
    if result["job"]["state"] != "done":
        args.output.write_text(json.dumps({"phase": "ticket-failed", "passed": False,
            "direction": args.direction, "mode": args.mode, "http_status": result.get("status"),
            "error_code": result.get("error"), "real_model_calls": 0}, indent=2) + "\n", encoding="utf-8")
        raise RuntimeError("Real ticket acquisition failed; inspect its sanitized receipt")
    ticket = controller.data("POST", "/api/v1/connector/ticket", {"job_id": job})
    status, _, second = controller.request("POST", "/api/v1/connector/ticket", {"job_id": job})
    assert status == 409 and second["error"]["code"] == "remote_ticket_unavailable"
    payload = {"origin": account["origin"], "owner": str(account["profile"]["id"]),
               "ticket": ticket, "deviceId": remote["device_id"], "localDeviceId": local["device_id"],
               "mode": args.mode, "direction": args.direction,
               "accountTag": hashlib.sha256(str(account["profile"]["id"]).encode()).hexdigest()[:12],
               "output": str(args.output.resolve())}
    completed = subprocess.run(["node", "tools/physical_auth_probe.mjs"], input=json.dumps(payload),
                               text=True, capture_output=True, timeout=65, cwd=dev.ROOT)
    payload.clear()
    ticket = account = other = None
    print(completed.stdout.strip())
    if completed.stderr:
        print("Child stderr withheld; inspect the sanitized result code")
    if args.output.is_file():
        result = json.loads(args.output.read_text(encoding="utf-8"))
        result["connector_ticket_delivered_once"] = True
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(completed.returncode)


if __name__ == "__main__":
    main()
