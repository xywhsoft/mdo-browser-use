"""Reject incomplete grouped Agent/stream evidence without a browser or model."""
import base64
from copy import deepcopy
import json
from pathlib import Path
import struct
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from acceptance_probe import validate
from agent_parallel_protocol import GROUPS, IMAGE_INDEXES, RESULT_COUNTS, ParallelConversation
from agent_protocol_dialects import CHAT, RESPONSES, ANTHROPIC, normalize
from agent_protocol_probe import MODELS, PROMPT, FINAL, streaming


class AgentParallelProtocolTests(unittest.TestCase):
    def test_every_streamed_call_retains_its_own_index_id_and_unicode_arguments(self):
        calls = [{"id": "original_" + str(i), "type": "function", "function": {
            "name": name, "arguments": json.dumps({"index": i, "text": "组 🦉 " + str(i)}, ensure_ascii=False)}}
            for i, name in enumerate(("browser_screenshot", "browser_act", "browser_screenshot"))]
        for protocol in (CHAT, RESPONSES, ANTHROPIC):
            with self.subTest(protocol=protocol):
                wire = streaming({"tool_calls": calls}, "tool_calls", MODELS[0], protocol).decode()
                events = [json.loads(line[6:]) for line in wire.splitlines() if line.startswith("data: ") and line != "data: [DONE]"]
                ids, names, fragments = {}, {}, {i: [] for i in range(3)}
                for event in events:
                    if protocol == CHAT:
                        for choice in event["choices"]:
                            for call in choice["delta"].get("tool_calls", []):
                                i = call["index"]
                                if "id" in call:
                                    ids[i], names[i] = call["id"], call["function"]["name"]
                                fragments[i].append(call["function"]["arguments"])
                    elif protocol == RESPONSES:
                        if event["type"] == "response.output_item.added":
                            ids[event["output_index"]], names[event["output_index"]] = event["item"]["call_id"], event["item"]["name"]
                        if event["type"] == "response.function_call_arguments.delta":
                            i = event["output_index"]
                            self.assertEqual(event["item_id"], "fc_" + calls[i]["id"])
                            fragments[i].append(event["delta"])
                    else:
                        if event["type"] == "content_block_start":
                            ids[event["index"]], names[event["index"]] = event["content_block"]["id"], event["content_block"]["name"]
                        if event["type"] == "content_block_delta":
                            fragments[event["index"]].append(event["delta"]["partial_json"])
                self.assertEqual([ids[i] for i in range(3)], [c["id"] for c in calls])
                self.assertEqual([names[i] for i in range(3)], [c["function"]["name"] for c in calls])
                for i in range(3):
                    self.assertEqual(len(fragments[i]), 2)
                    self.assertEqual("".join(fragments[i]), calls[i]["function"]["arguments"])
                if protocol == RESPONSES:
                    self.assertEqual([e["output_index"] for e in events if e["type"] == "response.output_item.done"], [2, 1, 0])
                    self.assertEqual([v["call_id"] for v in events[-1]["response"]["output"]], [c["id"] for c in calls])

    def test_responses_normalization_preserves_adjacent_group_and_original_results(self):
        items = [{"type": "function_call", "call_id": str(i), "name": "capture", "arguments": "{}"} for i in range(3)]
        items += [{"type": "function_call_output", "call_id": str(i), "output": "result " + str(i)} for i in range(3)]
        original = deepcopy(items)
        actual = normalize({"input": items}, RESPONSES)["messages"]
        self.assertEqual([c["id"] for c in actual[0]["tool_calls"]], ["0", "1", "2"])
        self.assertEqual([m["tool_call_id"] for m in actual[1:]], ["0", "1", "2"])
        self.assertEqual([m["content"] for m in actual[1:]], ["result 0", "result 1", "result 2"])
        self.assertEqual(items, original)

    def trace(self, vision=True, mutate=None):
        model = MODELS[0 if vision else 1]
        oracle = ParallelConversation(model, "client", "http://127.0.0.1:1/page")
        messages = [{"role": "user", "content": PROMPT}]
        requests = []
        for step in range(7):
            request = {"model": model, "stream": True, "messages": deepcopy(messages),
                       "tools": [{"function": {"name": name}} for name in set(oracle.tools)]}
            if mutate:
                mutate(step, request)
            requests.append(deepcopy(request))
            delta, finish = oracle.reply(normalize(request, CHAT))
            if step == 6:
                self.assertEqual((delta["content"], finish), (FINAL, "stop"))
                break
            results, companions = [], []
            for index, call in zip(GROUPS[step], delta["tool_calls"]):
                if index == 3:
                    text = "tool_failure: code=4\nstale_document: Observe the current document"
                else:
                    payload = {"id": "browser"} if index in (0, 8) else {"ok": True}
                    if index in (1, 6):
                        payload.update(document="document", text="Readable fixture text", frame_routing="native-isolated-v1")
                    if index == 5:
                        payload["verification"] = "field_state"
                    if index in IMAGE_INDEXES:
                        width, height = {2: (40, 30), 4: (20, 15), 7: (30, 20)}[index]
                        data = b"\x89PNG\r\n\x1a\n" + b"\0" * 8 + struct.pack(">II", width, height)
                        payload["image"] = {"media_type": "image/png", "bytes": len(data), "width": width, "height": height}
                    text = json.dumps(payload)
                content = text
                if index in IMAGE_INDEXES:
                    if vision:
                        content = [{"type": "text", "text": text}]
                        label = {"source": "tool_output", "tool_call_id": call["id"], "untrusted": True, "part_order": ["text", "image"]}
                        companions.append({"role": "user", "content": [{"type": "text", "text": json.dumps(label)},
                            {"type": "image_url", "image_url": {"url": "data:image/png;base64," + base64.b64encode(data).decode()}}]})
                    else:
                        content += "\nThis model cannot view images."
                results.append({"role": "tool", "tool_call_id": call["id"], "content": content})
            messages += [{"role": "assistant", **delta}] + results + companions
        return oracle, requests

    def test_full_grouped_oracle_keeps_three_images_and_continues_after_one_failure(self):
        for vision in (True, False):
            oracle, _ = self.trace(vision)
            self.assertEqual([r["tool_results"] for r in oracle.rows], list(RESULT_COUNTS))
            self.assertEqual([r["verified_images"] for r in oracle.rows], [0, 0, 0, 2, 2, 3, 3])
            self.assertEqual(set(oracle.images), set(IMAGE_INDEXES))

    def test_grouped_oracle_rejects_missing_duplicate_and_cross_associated_images(self):
        def swapped(step, request):
            if step == 3:
                companions = [m for m in request["messages"] if m.get("role") == "user" and isinstance(m.get("content"), list)]
                a, b = companions
                a["content"][1], b["content"][1] = b["content"][1], a["content"][1]
        def missing(step, request):
            if step == 3:
                request["messages"].pop()
        def duplicate(step, request):
            if step == 3:
                request["messages"].append(deepcopy(request["messages"][-1]))
        for mutation in (swapped, missing, duplicate):
            with self.subTest(mutation=mutation.__name__), self.assertRaises(RuntimeError):
                self.trace(mutate=mutation)

    def test_acceptance_refuses_group_collapse_or_missing_retained_image(self):
        cohorts = []
        for model in MODELS:
            prefix = "bu_" + model.removeprefix("browser-use-static-protocol").lstrip("-").replace("-", "_") + "_"
            vision = model.endswith("-vision")
            images = [{"call_index": i, "tool_call_id": prefix + str(i), "image": {
                "route": "vision" if vision else "text-projection", "bytes": 24, "width": 40, "height": 30,
                "sha256": str(i) * 64, "no_wire_image": True}, "retained_image": {
                    "bytes": 24, "sha256": str(i) * 64, "original_image_retained": True}} for i in IMAGE_INDEXES]
            cohorts.append({"model": model, "protocol": (CHAT, RESPONSES, ANTHROPIC)[len(cohorts) // 2], "passed": True,
                "tool_terminal_pairs": 9, "ordinary_failure_then_native_recovery": True, "group_sizes": [len(g) for g in GROUPS], "images": images,
                "requests": [{"step": i, "tool_results": RESULT_COUNTS[i], "chat_schema_verified": True,
                              "tool_group_sizes": [len(g) for g in GROUPS[:i]], "verified_images": (0, 0, 0, 2, 2, 3, 3)[i]} for i in range(7)]})
        good = {"passed": True, "real_model_calls": 0, "external_browser_pages": 0, "request_count": 42,
                "configuration_restored": True, "same_home": True, "protocols": [CHAT, RESPONSES, ANTHROPIC],
                "parallel_groups": True, "cohorts": cohorts}
        validate("agent-parallel-protocol", good)
        mutations = (lambda r: r.update(parallel_groups=False),
                     lambda r: r["cohorts"][0].update(group_sizes=[1] * 9),
                     lambda r: r["cohorts"][0]["images"].pop(),
                     lambda r: r["cohorts"][0]["images"][1]["retained_image"].update(sha256="2" * 64),
                     lambda r: r["cohorts"][0]["images"][0].update(tool_call_id="foreign"))
        for mutation in mutations:
            bad = deepcopy(good)
            mutation(bad)
            with self.assertRaises(RuntimeError):
                validate("agent-parallel-protocol", bad)


if __name__ == "__main__":
    unittest.main()
