"""Reject contradictory Agent wire evidence without any live model or browser."""
import base64
import json
from pathlib import Path
import struct
import sys
import unittest
from copy import deepcopy

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from agent_protocol_probe import (Conversation, FINAL, MODELS, PROMPT, TOOLS,
                                  configuration, streaming)
from acceptance_probe import validate


class AgentProtocolOracleTests(unittest.TestCase):
    def request(self, model, messages):
        return {"model": model, "stream": True, "messages": messages,
                "tools": [{"function": {"name": name}} for name in set(TOOLS)]}

    def test_both_full_traces_require_original_call_ids_and_no_retry(self):
        for model in MODELS:
            with self.subTest(model=model):
                conversation = Conversation(model, "client", "http://127.0.0.1:1/page")
                messages = [{"role": "user", "content": PROMPT}]
                data = b"\x89PNG\r\n\x1a\n" + b"\0" * 8 + struct.pack(">II", 40, 30)
                for index in range(7):
                    delta, finish = conversation.reply(self.request(model, messages))
                    if index == 6:
                        self.assertEqual(delta["content"], FINAL)
                        self.assertEqual(finish, "stop")
                        break
                    call = delta["tool_calls"][0]
                    args = json.loads(call["function"]["arguments"])
                    if index in (2, 3):
                        self.assertEqual(args["document"], "qa-stale-document" if index == 2 else "document")
                    payload = [{"id": "browser"}, {"ok": True, "document": "document",
                        "text": "Readable fixture text", "frame_routing": "native-isolated-v1"},
                        {"ok": False, "error": {"code": "stale_document"}},
                        {"ok": True, "verification": "field_state"},
                        {"ok": True, "image": {"media_type": "image/png", "bytes": len(data),
                                                 "width": 40, "height": 30}}, {"id": "browser"}][index]
                    content = json.dumps(payload)
                    if index == 2:
                        content = "tool_failure: code=4\nstale_document: Observe the current root document"
                    if index == 4:
                        if model == MODELS[0]:
                            content = [{"type": "text", "text": content}, {"type": "image_url",
                                "image_url": {"url": "data:image/png;base64," + base64.b64encode(data).decode()}}]
                        else:
                            content += "\nThis model cannot view images."
                    messages += [{"role": "assistant", **delta},
                                 {"role": "tool", "tool_call_id": call["id"], "content": content}]
                self.assertEqual(len(conversation.rows), 7)
                with self.assertRaisesRegex(RuntimeError, "Duplicate"):
                    conversation.reply(self.request(model, messages))

    def test_mismatched_tool_identity_does_not_advance(self):
        conversation = Conversation(MODELS[0], "client", "http://127.0.0.1:1/page")
        messages = [{"role": "user", "content": PROMPT}]
        delta, _ = conversation.reply(self.request(MODELS[0], messages))
        messages += [{"role": "assistant", **delta}, {"role": "tool", "tool_call_id": "foreign",
                                                          "content": '{"id":"browser"}'}]
        with self.assertRaisesRegex(RuntimeError, "identity mismatch"):
            conversation.reply(self.request(MODELS[0], messages))
        self.assertEqual(conversation.step, 1)

    def test_corrupt_or_missing_vision_image_rejected(self):
        conversation = Conversation(MODELS[0], "client", "http://127.0.0.1:1/page")
        payload = {"ok": True, "image": {"media_type": "image/png", "bytes": 30}}
        with self.assertRaisesRegex(RuntimeError, "exactly one"):
            conversation.check_image({"content": json.dumps(payload)}, payload)

    def test_text_projection_must_explicitly_disclose_unavailable_vision(self):
        conversation = Conversation(MODELS[1], "client", "http://127.0.0.1:1/page")
        payload = {"ok": True, "image": {"media_type": "image/png", "bytes": 30}}
        with self.assertRaisesRegex(RuntimeError, "explicit image projection"):
            conversation.check_image({"content": json.dumps(payload)}, payload)

    def test_fixture_config_retains_builtin_and_never_sets_default_or_credential(self):
        config = configuration(1234)["patch"]
        self.assertNotIn("default_model", config)
        provider = config["providers"][-1]
        self.assertNotIn("credential", provider)
        self.assertEqual(list(provider["endpoints"].values()), ["http://127.0.0.1:1234/v1/chat/completions"])
        self.assertTrue(any(p["id"] == "ornith" for p in config["providers"]))
        for port in (0, 65536, True):
            with self.assertRaises(RuntimeError):
                configuration(port)

    def test_real_sse_fragments_reassemble_exact_unicode_arguments(self):
        arguments = json.dumps({"text": "Agent protocol 🦉"}, ensure_ascii=False)
        body = streaming({"tool_calls": [{"id": "c1", "function": {
            "name": "browser_act", "arguments": arguments}}]}, "tool_calls", MODELS[0])
        events = [json.loads(line[6:]) for line in body.decode().splitlines()
                  if line.startswith("data: ") and line != "data: [DONE]"]
        fragments = [c["function"]["arguments"] for e in events for choice in e["choices"]
                     for c in choice["delta"].get("tool_calls", [])]
        self.assertEqual(len(fragments), 2)
        self.assertEqual("".join(fragments), arguments)
        self.assertEqual(events[-2]["choices"][0]["finish_reason"], "tool_calls")
        self.assertTrue(body.endswith(b"data: [DONE]\n\n"))

    def test_acceptance_rejects_missing_original_image_or_failed_restoration(self):
        cohorts = []
        for index, model in enumerate(MODELS):
            image = {"route": "vision" if not index else "text-projection", "bytes": 30,
                     "width": 40, "height": 30, "sha256": "a" * 64, "no_wire_image": True}
            cohorts.append({"model": model, "passed": True, "tool_terminal_pairs": 6,
                "ordinary_failure_then_native_recovery": True, "image": image,
                "retained_image": {"bytes": 30, "sha256": "a" * 64, "original_image_retained": True},
                "requests": [{"step": i, "tool_results": i} for i in range(7)]})
        good = {"passed": True, "real_model_calls": 0, "external_browser_pages": 0, "request_count": 14,
                "configuration_restored": True, "same_home": True, "protocol": "openai-chat-completions", "cohorts": cohorts}
        validate("agent-protocol", good)
        for key in ("configuration_restored", "same_home"):
            bad = deepcopy(good)
            bad[key] = False
            with self.assertRaises(RuntimeError):
                validate("agent-protocol", bad)
        for index in range(2):
            bad = deepcopy(good)
            bad["cohorts"][index]["retained_image"]["original_image_retained"] = False
            with self.assertRaises(RuntimeError):
                validate("agent-protocol", bad)
        bad = deepcopy(good)
        bad["cohorts"][0]["image"]["sha256"] = "b" * 64
        with self.assertRaises(RuntimeError):
            validate("agent-protocol", bad)
        bad = deepcopy(good)
        bad["cohorts"][1] = bad["cohorts"][0]
        with self.assertRaises(RuntimeError):
            validate("agent-protocol", bad)


if __name__ == "__main__":
    unittest.main()
