"""Strict static wire adapters; no inference, network client or product fallback."""
import json

CHAT = "openai-chat-completions"
RESPONSES = "openai-responses"
ANTHROPIC = "anthropic-messages"
PROTOCOLS = (CHAT, RESPONSES, ANTHROPIC)
PATHS = {CHAT: "/v1/chat/completions", RESPONSES: "/v1/responses", ANTHROPIC: "/v1/messages"}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def content(parts):
    if isinstance(parts, str):
        return parts
    require(isinstance(parts, list), "Unexpected protocol content shape")
    result = []
    for part in parts:
        kind = part.get("type")
        if kind in ("text", "input_text", "output_text"):
            result.append({"type": "text", "text": part["text"]})
        elif kind == "input_image":
            require(isinstance(part.get("image_url"), str), "Responses image lost its original URL/bytes")
            result.append({"type": "image_url", "image_url": {"url": part["image_url"]}})
        elif kind == "image":
            source = part.get("source", {})
            require(source.get("type") == "base64" and source.get("media_type") == "image/png",
                    "Unexpected Anthropic screenshot image source")
            result.append({"type": "image_url", "image_url": {
                "url": "data:" + source["media_type"] + ";base64," + source["data"]}})
        else:
            raise RuntimeError("Unsupported static protocol content kind")
    return result


def normalize(request, protocol):
    """Expose real wire messages to the shared oracle without inventing results."""
    if protocol == CHAT:
        pending, seen, results, attachments = set(), set(), {}, {}
        for message in request.get("messages", []):
            if message.get("role") == "tool":
                output = message.get("content")
                require(isinstance(output, str) or isinstance(output, list) and output and
                        all(p.get("type") == "text" and isinstance(p.get("text"), str) for p in output),
                        "Chat tool content must contain only text")
                identity = message.get("tool_call_id")
                require(identity in pending, "Chat tool result does not match an original pending call")
                pending.remove(identity)
                results[identity] = message
            else:
                require(not pending, "Chat message appeared before all parallel tool results")
                parts = message.get("content")
                if message.get("role") == "user" and isinstance(parts, list) and parts and parts[0].get("type") == "text":
                    try:
                        label = json.loads(parts[0].get("text", ""))
                    except (ValueError, TypeError):
                        label = None
                    if isinstance(label, dict) and label.get("source") == "tool_output":
                        identity = label.get("tool_call_id")
                        require(identity in results and identity not in attachments and label.get("untrusted") is True,
                                "Chat media source identity was lost or duplicated")
                        text = results[identity]["content"]
                        text_count = len(text) if isinstance(text, list) else int(bool(text))
                        order = label.get("part_order", [])
                        require(isinstance(order, list) and order.count("text") == text_count and
                                [p for p in order if p != "text"] == ["image"] * (len(parts) - 1) and len(parts) > 1 and
                                all(p.get("type") == "image_url" and isinstance(p.get("image_url", {}).get("url"), str)
                                    for p in parts[1:]), "Chat media order/bytes were not preserved")
                        attachments[identity] = parts[1:]
                for call in message.get("tool_calls", []):
                    require(call["id"] not in seen, "Chat tool call was duplicated")
                    pending.add(call["id"])
                    seen.add(call["id"])
        require(not pending, "Chat tool result group is incomplete")
        return dict(request, _chat_schema_verified=True, _tool_media_by_id=attachments)
    messages = []
    if protocol == RESPONSES:
        require(not request.get("previous_response_id"), "Static Agent must send its original stateless history")
        require(isinstance(request.get("input"), list), "Responses input items missing")
        for item in request["input"]:
            kind = item.get("type")
            if kind == "function_call":
                call = {"id": item["call_id"], "type": "function",
                        "function": {"name": item["name"], "arguments": item["arguments"]}}
                if messages and messages[-1].get("role") == "assistant" and "tool_calls" in messages[-1]:
                    messages[-1]["tool_calls"].append(call)
                else:
                    messages.append({"role": "assistant", "tool_calls": [call]})
            elif kind == "function_call_output":
                messages.append({"role": "tool", "tool_call_id": item["call_id"], "content": content(item["output"])})
            elif item.get("role") in ("user", "assistant"):
                messages.append({"role": item["role"], "content": content(item["content"])})
            else:
                raise RuntimeError("Unexpected Responses history item")
        tools = [{"function": {"name": t["name"]}} for t in request.get("tools", []) if t.get("type") == "function"]
    else:
        require(protocol == ANTHROPIC and isinstance(request.get("messages"), list), "Unknown static protocol")
        for message in request["messages"]:
            parts = message.get("content", [])
            if isinstance(parts, str):
                messages.append(message)
                continue
            require(isinstance(parts, list), "Anthropic content blocks missing")
            calls, ordinary = [], []
            for part in parts:
                if part.get("type") == "tool_use":
                    require(message["role"] == "assistant", "Anthropic tool_use has wrong role")
                    calls.append({"id": part["id"], "type": "function", "function": {
                        "name": part["name"], "arguments": json.dumps(part["input"], ensure_ascii=False)}})
                elif part.get("type") == "tool_result":
                    require(message["role"] == "user", "Anthropic tool_result has wrong role")
                    messages.append({"role": "tool", "tool_call_id": part["tool_use_id"], "content": content(part["content"])})
                else:
                    ordinary.append(part)
            if calls or ordinary:
                value = {"role": message["role"], "content": content(ordinary)}
                if calls:
                    value["tool_calls"] = calls
                messages.append(value)
        tools = [{"function": {"name": t["name"]}} for t in request.get("tools", [])]
    return {"model": request.get("model"), "stream": request.get("stream"), "messages": messages, "tools": tools}


def streaming(delta, finish, model, protocol):
    """Official wire event shapes, with fragmented original JSON arguments."""
    events, calls, items = [], delta.get("tool_calls", []), []
    if protocol == RESPONSES:
        events.append({"type": "response.created", "response": {"id": "resp_static", "status": "in_progress", "output": []}})
        for index, call in enumerate(calls):
            item = {"id": "fc_" + call["id"], "type": "function_call", "call_id": call["id"],
                    "name": call["function"]["name"], "arguments": "", "status": "in_progress"}
            items.append(item)
            events.append({"type": "response.output_item.added", "output_index": index, "item": item.copy()})
        # Exercise item identity with interleaved fragments and reverse completion.
        for half in range(2):
            for index in (range(len(calls)) if half == 0 else reversed(range(len(calls)))):
                argument = calls[index]["function"]["arguments"]
                cut = max(1, len(argument) // 2)
                events.append({"type": "response.function_call_arguments.delta", "item_id": items[index]["id"],
                               "output_index": index, "delta": argument[:cut] if half == 0 else argument[cut:]})
        for index in reversed(range(len(calls))):
            item = items[index]
            item.update(arguments=calls[index]["function"]["arguments"], status="completed")
            events.append({"type": "response.function_call_arguments.done", "item_id": item["id"],
                           "output_index": index, "arguments": item["arguments"]})
            events.append({"type": "response.output_item.done", "output_index": index, "item": item.copy()})
        if not calls:
            item = {"id": "msg_static", "type": "message", "role": "assistant", "status": "completed",
                    "content": [{"type": "output_text", "text": delta["content"], "annotations": []}]}
            items.append(item)
            events.append({"type": "response.output_text.delta", "item_id": item["id"],
                           "output_index": 0, "content_index": 0, "delta": delta["content"]})
            events.append({"type": "response.output_item.done", "output_index": 0, "item": item})
        events.append({"type": "response.completed", "response": {"id": "resp_static", "model": model,
                       "status": "completed", "output": items, "usage": {
                           "input_tokens": 1000, "output_tokens": 50, "total_tokens": 1050}}})
    else:
        require(protocol == ANTHROPIC, "Unknown static SSE protocol")
        events.append({"type": "message_start", "message": {"id": "msg_static", "type": "message",
                       "role": "assistant", "model": model, "content": [], "stop_reason": None,
                       "stop_sequence": None, "usage": {"input_tokens": 1000, "output_tokens": 0}}})
        for index, call in enumerate(calls):
            events.append({"type": "content_block_start", "index": index, "content_block": {
                "type": "tool_use", "id": call["id"], "name": call["function"]["name"], "input": {}}})
            argument = call["function"]["arguments"]
            cut = max(1, len(argument) // 2)
            for fragment in (argument[:cut], argument[cut:]):
                events.append({"type": "content_block_delta", "index": index, "delta": {
                    "type": "input_json_delta", "partial_json": fragment}})
            events.append({"type": "content_block_stop", "index": index})
        if not calls:
            events += [{"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}},
                       {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": delta["content"]}},
                       {"type": "content_block_stop", "index": 0}]
        events += [{"type": "message_delta", "delta": {"stop_reason": "tool_use" if calls else "end_turn", "stop_sequence": None},
                    "usage": {"output_tokens": 50}}, {"type": "message_stop"}]
    return "".join("event: " + e["type"] + "\ndata: " + json.dumps(e, ensure_ascii=False) + "\n\n" for e in events).encode()
