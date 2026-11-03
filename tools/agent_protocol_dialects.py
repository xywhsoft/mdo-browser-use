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
        return request
    messages = []
    if protocol == RESPONSES:
        require(not request.get("previous_response_id"), "Static Agent must send its original stateless history")
        require(isinstance(request.get("input"), list), "Responses input items missing")
        for item in request["input"]:
            kind = item.get("type")
            if kind == "function_call":
                messages.append({"role": "assistant", "tool_calls": [{"id": item["call_id"], "type": "function",
                    "function": {"name": item["name"], "arguments": item["arguments"]}}]})
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
    events = []
    call = delta.get("tool_calls", [None])[0]
    if protocol == RESPONSES:
        events.append({"type": "response.created", "response": {"id": "resp_static", "status": "in_progress", "output": []}})
        if call:
            item = {"id": "fc_" + call["id"], "type": "function_call", "call_id": call["id"],
                    "name": call["function"]["name"], "arguments": "", "status": "in_progress"}
            events.append({"type": "response.output_item.added", "output_index": 0, "item": item.copy()})
            argument = call["function"]["arguments"]
            cut = max(1, len(argument) // 2)
            for fragment in (argument[:cut], argument[cut:]):
                events.append({"type": "response.function_call_arguments.delta", "item_id": item["id"],
                               "output_index": 0, "delta": fragment})
            item.update(arguments=argument, status="completed")
            events.append({"type": "response.function_call_arguments.done", "item_id": item["id"],
                           "output_index": 0, "arguments": argument})
        else:
            item = {"id": "msg_static", "type": "message", "role": "assistant", "status": "completed",
                    "content": [{"type": "output_text", "text": delta["content"], "annotations": []}]}
            events.append({"type": "response.output_text.delta", "item_id": item["id"],
                           "output_index": 0, "content_index": 0, "delta": delta["content"]})
        events.append({"type": "response.output_item.done", "output_index": 0, "item": item})
        events.append({"type": "response.completed", "response": {"id": "resp_static", "model": model,
                       "status": "completed", "output": [item], "usage": {
                           "input_tokens": 1000, "output_tokens": 50, "total_tokens": 1050}}})
    else:
        require(protocol == ANTHROPIC, "Unknown static SSE protocol")
        events.append({"type": "message_start", "message": {"id": "msg_static", "type": "message",
                       "role": "assistant", "model": model, "content": [], "stop_reason": None,
                       "stop_sequence": None, "usage": {"input_tokens": 1000, "output_tokens": 0}}})
        if call:
            events.append({"type": "content_block_start", "index": 0, "content_block": {
                "type": "tool_use", "id": call["id"], "name": call["function"]["name"], "input": {}}})
            argument = call["function"]["arguments"]
            cut = max(1, len(argument) // 2)
            for fragment in (argument[:cut], argument[cut:]):
                events.append({"type": "content_block_delta", "index": 0, "delta": {
                    "type": "input_json_delta", "partial_json": fragment}})
        else:
            events += [{"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}},
                       {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": delta["content"]}}]
        events += [{"type": "content_block_stop", "index": 0},
                   {"type": "message_delta", "delta": {"stop_reason": "tool_use" if call else "end_turn", "stop_sequence": None},
                    "usage": {"output_tokens": 50}}, {"type": "message_stop"}]
    return "".join("event: " + e["type"] + "\ndata: " + json.dumps(e, ensure_ascii=False) + "\n\n" for e in events).encode()
