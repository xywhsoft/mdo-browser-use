"""Exercise actual native serializer/cache bodies, not a mirrored implementation."""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import dev
from agent_protocol_dialects import CHAT, normalize
from prepare_model_transport import reviewed_source


@unittest.skipUnless(os.name == "nt", "Pinned Windows native compiler required")
class ChatToolMediaTests(unittest.TestCase):
    def test_production_serializer_parallel_media_cache_and_allocation_failures(self):
        native = dev.ENV / "native-source"
        spec = json.loads((dev.ROOT / "host/model-transport.json").read_text())
        record = next(r for r in spec["files"] if r["path"].endswith("dialect_completions.c"))
        before = subprocess.check_output(["git", "show", "HEAD:" + record["path"]], cwd=native).decode().replace("\r\n", "\n")
        record = json.loads(json.dumps(record))
        for change in record["changes"]:
            if "after_file" in change:
                change["after_file"] = change["after_file"].removeprefix("browser-use-")
        serializer = reviewed_source(record, before, dev.ROOT / "host")
        serializer = serializer[:serializer.index("/* Response decoding")].rsplit("/* ------------------------------------------------------------------ */", 1)[0]
        client = (native / "lib/xllm/src/xllm_client.c").read_text(encoding="utf-8")
        client = client[client.index("static char* xllm__client_serialize_body(xllm_client* pClient,\n    const xllm_request* pRequest, xllm_error* pError)\n{"):
                        client.index("\nxllm_call* xllmClientStart(")]
        directory = dev.ENV / "qa/model-transport"
        directory.mkdir(parents=True, exist_ok=True)
        source, xrt, binary = directory / "chat.c", directory / "xrt.c", directory / "chat.exe"
        body = "#define XLLM_TEST_ALLOCATOR\n"
        for name in ("xllm_core.c", "xllm_parts.c"):
            body += '#include "' + (native / "lib/xllm/src" / name).as_posix() + '"\n'
        body += serializer + client + (dev.ROOT / "tests/fixtures/chat-tool-media-checks.c").read_text()
        source.write_text(body, encoding="utf-8", newline="\n")
        xrt.write_text('#define XRT_MODULE_JSON_READ\n#define XRT_MODULE_CODEC_BASE64\n#define XRT_IMPLEMENTATION\n#include "' +
                       (native / "lib/xrt.h").as_posix() + '"\n', encoding="utf-8", newline="\n")
        compiler = Path("E:/software/w64devkit/bin/gcc.exe")
        built = subprocess.run([str(compiler), "-std=c11", "-O1", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
            "-I" + str(native / "lib"), "-I" + str(native / "lib/xllm/src"), str(source), str(xrt), "-o", str(binary)], capture_output=True, text=True,
            timeout=60, cwd=dev.ROOT)
        self.assertEqual(built.returncode, 0, built.stderr[-5000:])
        result = subprocess.run([str(binary)], capture_output=True, text=True, encoding="utf-8", timeout=20,
            env=dict(os.environ, PATH=str(compiler.parent) + os.pathsep + os.environ.get("PATH", "")))
        self.assertEqual(result.returncode, 0, result.stderr[-2000:] + result.stdout[-800:])
        wire, summary = [json.loads(line) for line in result.stdout.splitlines()]
        actual = normalize(wire, CHAT)
        self.assertTrue(actual["_chat_schema_verified"])
        messages = wire["messages"]
        self.assertEqual([m["role"] for m in messages], ["user", "assistant", "tool", "tool", "tool", "user", "user", "user"])
        self.assertEqual(messages[2]["content"], "")
        self.assertEqual([p["text"] for p in messages[3]["content"]], ['caption A\n"', "caption B"])
        self.assertEqual(messages[4]["content"], "final tool text")
        media = actual["_tool_media_by_id"]
        self.assertEqual(list(media), ["two", 'one"\n🦉'])
        self.assertEqual(media["two"][0]["image_url"]["detail"], "low")
        first, second = media['one"\n🦉']
        self.assertEqual(base64.b64decode(first["image_url"]["url"].split(",")[1], validate=True), b"\x89PNG\r\n\x1a\n\0\0\0\0")
        self.assertEqual(second["image_url"], {"url": 'https://fixture.invalid/one.png?q="', "detail": "high"})
        self.assertNotIn("private reasoning", result.stdout)
        self.assertEqual(summary["cache_cuts"], 7)
        self.assertGreater(summary["oom_failures"], 5)
        self.assertEqual(summary["live_blocks"], 0)
        self.assertTrue(summary["source_unchanged"])
        proof = {"passed": True, "wire": wire, "summary": summary, "reviewed_serializer_sha256": record["after_sha256"],
                 "inputs": {str(p.relative_to(dev.ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in (
                     dev.ROOT / "host/model-transport.json", dev.ROOT / "host/chat-tool-media.inc.c",
                     dev.ROOT / "tests/fixtures/chat-tool-media-checks.c", Path(__file__), native / "lib/xllm/src/xllm_client.c")}}
        (dev.ENV / "chat-tool-media-result.json").write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
        print(summary)


if __name__ == "__main__":
    unittest.main()
