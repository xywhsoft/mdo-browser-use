"""Full Agent turns against a loopback static protocol fixture, never an LLM.

Uses the original deployment/Home. Refuses an existing model override rather
than copying credentials or replacing any user's provider configuration.
"""
import base64
import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import struct
import threading
import time
import uuid

import acceptance_probe
import dev
from agent_protocol_dialects import PROTOCOLS, PATHS, normalize, streaming as dialect_streaming

PROVIDER = "browser-use-static-protocol"
MODELS = tuple(PROVIDER + suffix + route for suffix in ("", "-responses", "-anthropic") for route in ("-vision", "-text"))
PROTOCOL_BY_MODEL = {model: PROTOCOLS[i // 2] for i, model in enumerate(MODELS)}
PROMPT = "Run the local Browser Use protocol fixture; no external task or delegation."
FINAL = "Browser Use local protocol fixture completed."
TOOLS = ("browser_open", "browser_observe", "browser_act", "browser_act",
         "browser_screenshot", "browser_open")


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def text_content(message):
    content = message.get("content", "")
    if isinstance(content, str):
        return content
    require(isinstance(content, list), "Unexpected wire content shape")
    return "\n".join(p["text"] for p in content if p.get("type") == "text")


def value(message):
    text = text_content(message)
    require("{" in text, "Browser result did not contain structured text")
    # A text-only projection may append the explicit image-unavailable notice.
    return json.JSONDecoder().raw_decode(text[text.index("{"):])[0]


def configuration(port):
    require(type(port) is int and 0 < port < 65536, "Invalid fixture port")
    baseline = json.loads((dev.SOURCE / "app/default-home/config/defaults.json").read_text(encoding="utf-8"))["models"]
    provider = {"id": PROVIDER, "name": "Local static protocol fixture (not a model)",
                "builtin": False, "editable": True, "removable": True,
                "verify_peer": True, "timeout_ms": 15000,
                "endpoints": {name: f"http://127.0.0.1:{port}" + PATHS[protocol] for name, protocol in
                              zip(("chat_completions", "responses", "anthropic_messages"), PROTOCOLS)}}
    models = []
    for name in MODELS:
        capabilities = ["text-input", "tool-result-input", "text-output",
                        "tool-call-output", "streaming", "reasoning-control", "parallel-tool-calls"]
        if name.endswith("-vision"):
            capabilities.append("media-input")
        models.append({"id": name, "name": name, "wire_model": name,
                       "provider": PROVIDER, "builtin": False, "free": True,
                       "editable": True, "removable": True,
                       "protocols": [PROTOCOL_BY_MODEL[name]], "default_protocol": PROTOCOL_BY_MODEL[name],
                       "capabilities": capabilities,
                       "window": {"mode": "shared-context", "context_tokens": 240128,
                                  "max_input_tokens": 240127, "max_output_tokens": 1024,
                                  "output_reserve_tokens": 1024, "summary_tokens": 512},
                       "reasoning_efforts": ["none"], "default_reasoning_effort": "none",
                       "attachments": ["image"] if name.endswith("-vision") else []})
    return {"schema_version": 1, "patch": {
        "providers": baseline["providers"] + [provider], "items": baseline["items"] + models}}


class Conversation:
    """Request-state oracle; no model inference, external IO or stored prompts."""
    tools, failure_indexes, expected_requests = TOOLS, {2}, 7

    def image_evidence(self, session):
        return {"image": self.image, "retained_image": retained_image(session, self)}

    def __init__(self, model, client, page):
        self.model, self.client, self.page = model, client, page
        self.step, self.browser, self.document = 0, None, None
        self.rows, self.image = [], None
        self.attempts = []

    def reply(self, request):
        require(self.step <= len(TOOLS), "Duplicate or extra model-protocol request")
        require(request.get("model") == self.model, "Unexpected wire model")
        require(request.get("stream") is True, "The actual Agent did not request streaming")
        messages = request.get("messages", [])
        users = [m for m in messages if m["role"] == "user"]
        require(any(PROMPT in text_content(m) for m in users), "Original QA prompt missing")
        names = {t["function"]["name"] for t in request.get("tools", [])}
        require(set(TOOLS) <= names, "Browser Use schemas were not exposed to the Agent")
        calls = [t for m in messages for t in m.get("tool_calls", [])]
        results = [m for m in messages if m["role"] == "tool"]
        latest = results[-1] if results else {"content": ""}
        latest_parts = latest["content"] if isinstance(latest["content"], list) else []
        self.attempts.append({"step": self.step, "tool_results": len(results),
            "latest_tool_text_bytes": len(text_content(latest).encode()),
            "latest_tool_image_parts": len([p for p in latest_parts if p.get("type") == "image_url"])})
        require(len(calls) == self.step and len(results) == self.step,
                "Missing, duplicated or unrelated tool messages at the model boundary")
        for index, (call, result) in enumerate(zip(calls, results)):
            require(call["id"] == self.call_id(index) == result.get("tool_call_id") and
                    call["function"]["name"] == TOOLS[index], "Tool call/result identity mismatch")
            require(json.loads(call["function"]["arguments"]) == self.arguments(index),
                    "Original streamed tool arguments were changed")
        if self.step:
            result = results[-1]
            # The normal Agent executor wraps failures as plain actionable text,
            # unlike the standalone SDK result envelope. Check that real shape.
            payload = {} if self.step == 3 else value(result)
            if self.step == 1:
                self.browser = payload.get("id")
                require(isinstance(self.browser, str) and self.browser, "Create result lost browser ID")
            elif self.step == 2:
                self.document = payload.get("document")
                require(payload.get("ok") and "Readable fixture text" in payload.get("text", "") and
                        payload.get("frame_routing") == "native-isolated-v1" and self.document,
                        "Agent observation did not come from the actual native fixture")
            elif self.step == 3:
                require("tool_failure:" in text_content(result) and "stale_document" in text_content(result),
                        "Ordinary tool failure was not returned to the same Agent turn")
            elif self.step == 4:
                require(payload.get("ok") and payload.get("verification") == "field_state",
                        "Next native fill did not succeed after the ordinary failure")
            elif self.step == 5:
                self.image = self.check_image(result, payload, request.get("_tool_media_by_id", {}).get(result["tool_call_id"]))
            elif self.step == 6:
                require(payload.get("id") == self.browser, "Close result lost the original browser identity")
        self.rows.append({"step": self.step, "request_bytes": request.get("_wire_bytes", len(json.dumps(request).encode())),
                          "chat_schema_verified": request.get("_chat_schema_verified", False),
                          "tool_results": len(results), "image_verified": self.image is not None})
        if self.step == len(TOOLS):
            self.step += 1
            return {"content": FINAL}, "stop"
        arguments = self.arguments(self.step)
        call = {"id": self.call_id(self.step), "type": "function", "function": {
            "name": TOOLS[self.step], "arguments": json.dumps(arguments, ensure_ascii=False)}}
        self.step += 1
        return {"tool_calls": [call]}, "tool_calls"

    def call_id(self, step):
        return "bu_" + self.model.removeprefix(PROVIDER).lstrip("-").replace("-", "_") + "_" + str(step)

    def arguments(self, step):
        if step == 0:
            return {"action": "create", "client": self.client, "url": self.page}
        if step == 1:
            return {"browser": self.browser}
        if step in (2, 3):
            return {"browser": self.browser, "document": "qa-stale-document" if step == 2 else self.document,
                    "op": "fill", "target": {"label": "订单号"}, "text": "Agent protocol 🦉"}
        if step == 4:
            return {"browser": self.browser, "document": self.document, "format": "png",
                    "max_width": 400, "max_height": 300}
        return {"action": "close", "browser": self.browser}

    def check_image(self, message, payload, companion=None):
        meta = payload.get("image", {})
        text = text_content(message)
        require(payload.get("ok") and meta.get("media_type") == "image/png" and
                "base64" not in text, "Screenshot metadata/text was corrupted")
        content = message.get("content")
        images = [p for p in content if p.get("type") == "image_url"] if isinstance(content, list) else []
        if companion is not None:
            require(not images, "Chat tool image remained in the text-only wire result")
            images = companion
        if self.model.endswith("-text"):
            require(not images and isinstance(content, str) and "cannot view images" in text,
                    "Text-only model did not receive the explicit image projection")
            return {"route": "text-projection", "bytes": meta["bytes"], "no_wire_image": True}
        require(len(images) == 1, "Vision model did not receive exactly one screenshot image")
        url = images[0]["image_url"]["url"]
        require(url.startswith("data:image/png;base64,"), "Screenshot wire media type changed")
        data = base64.b64decode(url.split(",", 1)[1], validate=True)
        require(data.startswith(b"\x89PNG\r\n\x1a\n") and len(data) == meta["bytes"],
                "Screenshot wire bytes differ from original metadata")
        width, height = struct.unpack(">II", data[16:24])
        require(0 < width <= 400 and 0 < height <= 300 and
                (width, height) == (meta["width"], meta["height"]), "Screenshot dimensions changed in transit")
        return {"route": "vision", "bytes": len(data), "sha256": sha(data), "width": width, "height": height}


def streaming(delta, finish, model, protocol=PROTOCOLS[0]):
    """Fragment argument JSON across real SSE chunks, including Unicode."""
    if protocol != PROTOCOLS[0]:
        return dialect_streaming(delta, finish, model, protocol)
    def chunk(value, reason=None):
        return {"id": "static-" + model, "object": "chat.completion.chunk", "model": model,
                "choices": [{"index": 0, "delta": value, "finish_reason": reason}]}
    frames = [chunk({"role": "assistant"})]
    if "tool_calls" in delta:
        calls = delta["tool_calls"]
        for index, call in enumerate(calls):
            argument = call["function"]["arguments"]
            cut = max(1, len(argument) // 2)
            frames.append(chunk({"tool_calls": [{"index": index, "id": call["id"], "type": "function",
                           "function": {"name": call["function"]["name"], "arguments": argument[:cut]}}]}))
        for index in reversed(range(len(calls))):
            argument = calls[index]["function"]["arguments"]
            cut = max(1, len(argument) // 2)
            frames.append(chunk({"tool_calls": [{"index": index, "function": {"arguments": argument[cut:]}}]}))
    else:
        frames.append(chunk(delta))
    frames.append(chunk({}, finish))
    frames.append({"id": "static-" + model, "object": "chat.completion.chunk", "model": model,
                   "choices": [], "usage": {"prompt_tokens": 1000, "completion_tokens": 50, "total_tokens": 1050}})
    return ("".join("data: " + json.dumps(f, ensure_ascii=False) + "\n\n" for f in frames) + "data: [DONE]\n\n").encode()


class Fixture(ThreadingHTTPServer):
    # Native Chromium may open an idle speculative connection. It must not
    # occupy the model response listener. Tests/Agent turns still run serially.
    daemon_threads = False

    def __init__(self):
        super().__init__(("127.0.0.1", 0), Handler)
        self.conversations, self.errors = {}, []
        self.requests_total = 0
        self.timeout = 1


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        self.request.settimeout(2)
        super().setup()

    def log_message(self, *_args):
        pass  # Never log HTTP headers, bodies, prompt text or credentials.

    def do_GET(self):
        if self.path != "/page":
            self.send_error(404)
            return
        body = (dev.ROOT / "tests/fixtures/page.html").read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        try:
            self.server.requests_total += 1
            require(self.path in PATHS.values(), "Unexpected fixture path")
            require(not self.headers.get("Authorization") and not self.headers.get("Cookie") and not self.headers.get("X-Api-Key"),
                    "Credentials reached the static fixture")
            size = int(self.headers.get("Content-Length", "0"))
            require(0 < size <= 4 * 1024 * 1024, "Invalid fixture request size")
            request = json.loads(self.rfile.read(size))
            require(request.get("model") in self.server.conversations, "Unregistered static fixture model")
            model = request["model"]
            protocol = PROTOCOL_BY_MODEL[model]
            require(PATHS[protocol] == self.path, "Agent selected an unexpected protocol endpoint")
            normalized = normalize(request, protocol)
            normalized["_wire_bytes"] = size
            delta, finish = self.server.conversations[model].reply(normalized)
            body = streaming(delta, finish, model, protocol)
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as error:
            # Only controlled oracle errors are retained, never arbitrary input.
            message = str(error) if isinstance(error, RuntimeError) else type(error).__name__
            self.server.errors.append(message[:256])
            self.send_error(400, "Static fixture rejected the request")


def api(method, path, body=None):
    status, _, raw = dev.request(method, path, body)
    require(status in (200, 201, 202), "Agent QA API rejected " + method + " " + path)
    return json.loads(raw)["data"]


def events(session):
    rows, cursor = [], 0
    for _ in range(32):
        page = api("GET", f"/api/v1/projects/default/sessions/{session}/events?after={cursor}&limit=32")
        rows.extend(page["items"])
        if not page["items"] or page["items"][-1]["event_id"] >= page["latest_event_id"]:
            return rows
        cursor = page["items"][-1]["event_id"]
    raise RuntimeError("QA event stream exceeded its bounded reader")


def retained_image(session, conversation, index=4, image=None):
    image = conversation.image if image is None else image
    snapshot = dev.HOME / "sessions/default" / session / "snapshot.json"
    raw = snapshot.read_bytes()
    document = json.loads(raw)
    entries = [e for e in document["entries"] if e.get("role") == 3 and
               e.get("tool_call_id") == conversation.call_id(index)]
    require(len(entries) == 1, "Original screenshot has no unique durable tool message")
    parts = [p for p in entries[0].get("parts", []) if p.get("kind") == 2]
    require(len(parts) == 1 and parts[0].get("media_type") == "image/png",
            "Original screenshot image was lost from the saved conversation")
    data = base64.b64decode(parts[0]["data"], validate=True)
    require(len(data) == parts[0]["data_bytes"] == image["bytes"] and
            data.startswith(b"\x89PNG\r\n\x1a\n"), "Durable screenshot bytes changed")
    if conversation.model.endswith("-vision"):
        require(sha(data) == image["sha256"], "Vision wire image differs from the original saved screenshot")
    return {"bytes": len(data), "sha256": sha(data), "snapshot_sha256": sha(raw), "original_image_retained": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol", choices=PROTOCOLS, help="Only one protocol, for bounded diagnosis; not full acceptance")
    parser.add_argument("--parallel", action="store_true", help="Exercise grouped tool calls through the same actual Agent")
    args = parser.parse_args()
    oracle = Conversation
    if args.parallel:
        from agent_parallel_protocol import ParallelConversation
        oracle = ParallelConversation
    selected = tuple(m for m in MODELS if not args.protocol or PROTOCOL_BY_MODEL[m] == args.protocol)
    output = dev.ENV / ("agent-parallel-protocol-result.json" if args.parallel else "agent-protocol-result.json")
    report = {"passed": False, "state": "running", "real_model_calls": 0,
              "external_browser_pages": 0, "parallel_groups": args.parallel, "protocols": list(PROTOCOLS) if not args.protocol else [args.protocol], "cohorts": []}
    save = lambda: output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    save()
    config_path = dev.HOME / "config/models.json"
    require(not config_path.exists() and not config_path.is_symlink() and
            not config_path.with_suffix(".json.bak").exists(), "Existing model override preserved; fixture requires no override")
    report["runtime_before"] = acceptance_probe.runtime_identity()
    acceptance_probe.deployed_inputs()
    settings_before = {p.name: sha(p.read_bytes()) for p in (dev.HOME / "config").iterdir() if p.is_file()}
    # Refuse an explicit proxy; loopback traffic must not leave this computer.
    settings = json.loads((dev.HOME / "config/settings.json").read_text(encoding="utf-8"))
    proxy = settings.get("patch", {}).get("transport", {}).get("proxy", {})
    require(proxy.get("kind", "none") == "none", "Existing proxy settings preserved; fixture requires direct loopback")
    require(api("GET", "/api/v1/settings")["transport"]["proxy"]["kind"] == "none",
            "Effective proxy settings preserved; fixture requires direct loopback")
    fixture = Fixture()
    worker = threading.Thread(target=fixture.serve_forever, name="static-protocol-fixture", daemon=True)
    worker.start()
    fixture_bytes = (json.dumps(configuration(fixture.server_port), indent=2) + "\n").encode()
    changed, stopped, run, active_scope = False, False, None, None
    try:
        require(not api("GET", "/api/v1/runs")["active_runs"], "Do not restart an active Agent")
        require(not dev.call("operation.list"), "Do not restart active SDK operations")
        dev.stop()
        stopped = True
        with config_path.open("xb") as target:
            target.write(fixture_bytes)
        changed = True
        dev.start(window=True)
        clients, end = [], time.monotonic() + 25
        while not clients and time.monotonic() < end:
            clients = dev.call("ui.clients")
            if not clients:
                time.sleep(.1)
        require(len(clients) == 1, "Expected one actual main-interface display client")
        page = f"http://127.0.0.1:{fixture.server_port}/page"
        for model in selected:
            conversation = oracle(model, clients[0]["id"], page)
            fixture.conversations[model] = conversation
            session = uuid.uuid4().hex
            active_scope = {"extension": "plugin.mdo.browser-use", "project": "default", "session": session}
            created = api("POST", "/api/v1/sessions", {"project_id": "default", "client_session_id": session,
                "title": "Browser Use static protocol QA", "model_id": model, "protocol": PROTOCOL_BY_MODEL[model],
                "reasoning_effort": "none", "permission_profile": "full-access", "workspace_root": str(dev.ROOT)})
            require(created["model_id"] == model, "QA session selected an unexpected model")
            started = api("POST", f"/api/v1/projects/default/sessions/{session}/runs",
                          {"prompt": PROMPT, "timeout_ms": 90000})
            run = started["id"]
            report["active_session"], report["active_run"] = session, run
            end = time.monotonic() + 95
            while time.monotonic() < end:
                current = api("GET", "/api/v1/runs/" + run)
                if current["terminal"] or fixture.errors:
                    break
                time.sleep(.1)
            report["last_run"] = {k: current[k] for k in (
                "state", "result", "error_code", "terminal", "model_calls", "tool_calls")}
            if fixture.errors:
                raise RuntimeError("Static protocol oracle: " + fixture.errors[0])
            require(current["terminal"], "Local Agent protocol run did not terminate")
            require(current["state"] == "succeeded" and current["final_text"] == FINAL,
                    "Full Agent did not finish the original protocol turn")
            require(current["model_calls"] == conversation.expected_requests and
                    current["tool_calls"] == len(conversation.tools) and conversation.step == conversation.expected_requests,
                    "Full Agent call counts changed or an effect was retried")
            ledger = events(session)
            for index, name in enumerate(conversation.tools):
                pair = [e for e in ledger if e["tool_call_id"] == conversation.call_id(index)]
                require([e["kind"] for e in pair] == ["tool_start", "tool_done"] and
                        all(e["tool_name"] == name for e in pair), "Original tool did not get one durable terminal pair")
                require(pair[1]["success"] is (index not in conversation.failure_indexes), "Ordinary failure status was changed in the ledger")
            require(len([e for e in ledger if e["kind"] == "agent_done"]) == 1 and
                    not any(e["kind"] == "error" for e in ledger), "Agent terminal stream contradicted success")
            require(not dev.call("browseruse.tabs", {"action": "list"}, active_scope)["tabs"],
                    "Agent left its original browser open")
            report["cohorts"].append({"model": model, "session": session, "run": run,
                "passed": True, "requests": conversation.rows, **conversation.image_evidence(session),
                "protocol": PROTOCOL_BY_MODEL[model],
                "tool_terminal_pairs": len(conversation.tools), "ordinary_failure_then_native_recovery": True})
            save()
            run = None
        require(fixture.requests_total == 7 * len(selected), "Protocol transport repeated or omitted a request")
        report["passed"], report["state"] = True, "passed"
    except Exception as error:
        report.update(error_type=type(error).__name__, error=str(error)[:512], state="failed")
        raise
    finally:
        if run:
            try:
                api("DELETE", "/api/v1/runs/" + run)
            except (OSError, RuntimeError):
                pass
        try:
            if changed:
                if active_scope:
                    try:
                        for tab in dev.call("browseruse.tabs", {"action": "list"}, active_scope)["tabs"]:
                            dev.call("browseruse.tabs", {"action": "close", "browser": tab["id"]}, active_scope)
                    except (OSError, RuntimeError):
                        pass
                dev.stop()
                require(config_path.read_bytes() == fixture_bytes, "Concurrent model edit preserved; restore requires review")
                config_path.unlink()  # Exact known, newly created nonsecret fixture file only.
                require(not config_path.with_suffix(".json.bak").exists(), "Unexpected model backup preserved")
                dev.start()
            elif stopped:
                dev.start()
        except Exception as error:
            report.update(passed=False, state="restore_failed", restore_error=str(error)[:256])
            save()
            raise
        finally:
            fixture.shutdown()
            fixture.server_close()
            worker.join(timeout=3)
        report["runtime_after"] = acceptance_probe.runtime_identity()
        report["configuration_restored"] = settings_before == {
            p.name: sha(p.read_bytes()) for p in (dev.HOME / "config").iterdir() if p.is_file()}
        report["same_home"] = report["runtime_after"]["home"] == report["runtime_before"]["home"]
        report["request_count"] = fixture.requests_total
        report["protocol_attempts"] = {model: c.attempts for model, c in fixture.conversations.items()}
        report["source_sha256"] = acceptance_probe.source_inputs()
        report["deployed_inputs"] = acceptance_probe.deployed_inputs()
        if not report["configuration_restored"] or worker.is_alive():
            report.update(passed=False, state="failed")
        save()
    require(report["passed"], "Agent protocol fixture or restoration failed")
    print(json.dumps({k: v for k, v in report.items() if k not in ("source_sha256", "deployed_inputs")}, indent=2))


if __name__ == "__main__":
    main()
