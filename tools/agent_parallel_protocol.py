"""Grouped calls through the actual Agent; the fixture never fabricates tool results."""
import json

from agent_protocol_probe import Conversation, FINAL, PROMPT, require, retained_image, text_content, value

GROUPS = ((0,), (1,), (2, 3, 4), (5,), (6, 7), (8,))
RESULT_COUNTS = (0, 1, 2, 5, 6, 8, 9)
IMAGE_INDEXES = (2, 4, 7)


class ParallelConversation(Conversation):
    tools = ("browser_open", "browser_observe", "browser_screenshot", "browser_act",
             "browser_screenshot", "browser_act", "browser_observe", "browser_screenshot", "browser_open")
    failure_indexes = {3}

    def __init__(self, model, client, page):
        super().__init__(model, client, page)
        self.images = {}

    def arguments(self, index):
        if index == 0:
            return {"action": "create", "client": self.client, "url": self.page}
        if index in (1, 6):
            return {"browser": self.browser}
        if index in (3, 5):
            return {"browser": self.browser, "document": "qa-stale-document" if index == 3 else self.document,
                    "op": "fill", "target": {"label": "订单号"}, "text": "Grouped protocol 🦉"}
        if index in IMAGE_INDEXES:
            width, height = {2: (400, 300), 4: (160, 120), 7: (320, 240)}[index]
            return {"browser": self.browser, "document": self.document, "format": "png",
                    "max_width": width, "max_height": height}
        return {"action": "close", "browser": self.browser}

    def reply(self, request):
        require(self.step <= len(GROUPS), "Duplicate or extra grouped protocol request")
        require(request.get("model") == self.model and request.get("stream") is True,
                "Grouped fixture selected an unexpected model or nonstreaming request")
        messages = request.get("messages", [])
        require(any(m.get("role") == "user" and PROMPT in text_content(m) for m in messages), "Original QA prompt missing")
        require(set(self.tools) <= {t["function"]["name"] for t in request.get("tools", [])}, "Grouped tool schemas missing")
        batches = [m["tool_calls"] for m in messages if m.get("tool_calls")]
        results = [m for m in messages if m.get("role") == "tool"]
        calls = [call for batch in batches for call in batch]
        require([len(b) for b in batches] == [len(g) for g in GROUPS[:self.step]], "Original tool-call group was split or collapsed")
        require(len(calls) == len(results) == RESULT_COUNTS[self.step], "A grouped tool result was lost, duplicated or retried")
        for index, (call, result) in enumerate(zip(calls, results)):
            require(call["id"] == self.call_id(index) == result.get("tool_call_id") and
                    call["function"]["name"] == self.tools[index], "Grouped call/result identity or order changed")
            require(json.loads(call["function"]["arguments"]) == self.arguments(index), "Streamed grouped arguments changed")
            if index == 3:
                require("tool_failure:" in text_content(result) and "stale_document" in text_content(result),
                        "The failed member of a group was not preserved as an ordinary failure")
                continue
            payload = value(result)
            if index == 0:
                browser = payload.get("id")
                require(isinstance(browser, str) and browser, "Grouped create lost the original browser")
                require(self.browser in (None, browser), "Original grouped browser changed")
                self.browser = browser
            elif index in (1, 6):
                document = payload.get("document")
                require(payload.get("ok") and "Readable fixture text" in payload.get("text", "") and
                        payload.get("frame_routing") == "native-isolated-v1" and document,
                        "Grouped observation did not reach the actual native fixture")
                require(self.document in (None, document), "Grouped fixture unexpectedly navigated")
                self.document = document
            elif index in IMAGE_INDEXES:
                image = self.check_image(result, payload, request.get("_tool_media_by_id", {}).get(result["tool_call_id"]))
                argument = self.arguments(index)
                require(payload["image"]["width"] <= argument["max_width"] and
                        payload["image"]["height"] <= argument["max_height"], "Grouped image limits were cross-associated")
                require(index not in self.images or image == self.images[index], "A previous grouped image changed in later history")
                self.images[index] = image
            elif index == 5:
                require(payload.get("ok") and payload.get("verification") == "field_state", "The Agent did not recover after grouped failure")
            else:
                require(payload.get("id") == self.browser, "Grouped close lost its original browser")
        row = {"step": self.step, "request_bytes": request.get("_wire_bytes", 0), "tool_results": len(results),
               "chat_schema_verified": request.get("_chat_schema_verified", False),
               "tool_group_sizes": [len(b) for b in batches], "verified_images": len(self.images)}
        self.rows.append(row)
        self.attempts.append(row.copy())
        if self.step == len(GROUPS):
            require(set(self.images) == set(IMAGE_INDEXES), "Grouped screenshots were omitted")
            if self.model.endswith("-vision"):
                require(len({i["sha256"] for i in self.images.values()}) == 3, "Distinct screenshots were aliased")
            self.step += 1
            return {"content": FINAL}, "stop"
        group = GROUPS[self.step]
        calls = [{"id": self.call_id(index), "type": "function", "function": {
            "name": self.tools[index], "arguments": json.dumps(self.arguments(index), ensure_ascii=False)}} for index in group]
        self.step += 1
        return {"tool_calls": calls}, "tool_calls"

    def image_evidence(self, session):
        return {"group_sizes": [len(group) for group in GROUPS], "images": [
            {"call_index": index, "tool_call_id": self.call_id(index), "image": self.images[index],
             "retained_image": retained_image(session, self, index, self.images[index])} for index in IMAGE_INDEXES]}
