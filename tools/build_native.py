"""Build one reusable native host from the pinned SDK, without extra test copies."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

import dev

NATIVE = dev.ENV / "native-source"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("D:/GIT/xserver"))
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    if dev.state().get("pid"):
        raise RuntimeError("Stop the project host before rebuilding it")
    lock = json.loads((dev.SOURCE / "deps.lock").read_text(encoding="utf-8"))
    revision = lock["xserver"]["commit"]
    patch = dev.SOURCE / lock["sdk_overlay"]["path"]
    if hashlib.sha256(patch.read_bytes()).hexdigest() != lock["sdk_overlay"]["sha256"]:
        raise RuntimeError("Pinned SDK patch hash mismatch")
    if not NATIVE.exists():
        dev.run("git", "clone", "--shared", "--no-checkout", str(args.source.resolve()), str(NATIVE))
        dev.run("git", "checkout", "--detach", revision, cwd=NATIVE)
    if dev.run("git", "rev-parse", "HEAD", cwd=NATIVE) != revision:
        raise RuntimeError("Native source changed revision; refusing to reset it")
    if subprocess.run(["git", "apply", "--reverse", "--check", "--exclude=src/webview/xs_browser.inc.hpp", str(patch)], cwd=NATIVE, capture_output=True).returncode:
        dev.run("git", "apply", "--check", str(patch), cwd=NATIVE)
        dev.run("git", "apply", "--whitespace=nowarn", str(patch), cwd=NATIVE)
    base = (dev.SOURCE / "tools/native/xs_browser.inc.hpp").read_text(encoding="utf-8")
    changes = [
        ('#include <mutex>', '#include <mutex>\n#include <cmath>\n#include <vector>'),
        ('struct xb_view{', (dev.ROOT / 'host/native-dialog-state.inc.hpp').read_text(encoding='utf-8')+'\nstruct xb_view{'),
        ('int errorCode=0;UINT64 navigation=0;', 'int errorCode=0;UINT64 navigation=0;int automationContext=0;bool closed=false,dialogsEnabled=false;std::shared_ptr<xb_dialog> dialog;std::weak_ptr<xb_input_sequence> input;std::map<std::string,bool> automationRequests;'),
        ('~xb_view(){if(controller)', '~xb_view(){dialog.reset();xb_dialog_cleanup(window);if(controller)'),
        ('static void xb_receive(', '#include "xs_browser_automation.inc.hpp"\nstatic void xb_receive('),
        ('active->loading=true;active->error.clear();', 'active->loading=true;active->automationContext=0;active->error.clear();'),
        ('std::weak_ptr<xb_view> weak=view;', 'xb_install_dialogs(host,view);\n            std::weak_ptr<xb_view> weak=view;'),
        ('if(command=="close"){host->views.erase(browser);', 'if(command=="close"){if(found!=host->views.end()){found->second->closed=true;xb_abort_automation(host,found->second,false);found->second->dialog.reset();xb_dialog_cleanup(found->second->window);if(found->second->controller)found->second->controller->Close();if(found->second->window)ShowWindow(found->second->window,SW_HIDE);}host->views.erase(browser);'),
        ('const auto viewport=",\\"viewport_attached\\":', 'const auto viewport=",\\"navigation\\":"+xb_quote(std::to_string(view->navigation))+",\\"automation\\":true,\\"viewport_attached\\":'),
        ('+viewport+",\\"process_failed\\":', '+viewport+",\\"dialog_capability\\":"+(view->dialogsEnabled?"true":"false")+",\\"input_busy\\":"+(view->input.expired()?"false":"true")+",\\"dialog_open\\":"+(view->dialog?"true":"false")+",\\"process_failed\\":'),
        ('const bool mutation=command!="snapshot"&&command!="screenshot";', 'const bool mutation=command!="snapshot"&&command!="screenshot"&&!(command=="dialog"&&xb_get(json,"action")=="list");'),
        ('  HRESULT code=S_OK;', '  if(command=="dialog"){xb_dialog_request(host,view,id,json);return;}\n  if(command=="automation"){if(view->dialog){host->reply(id,"","A JavaScript dialog blocks page execution");return;}xb_automation(host,view,id,xb_get(json,"script"));return;}\n  if(command=="input"){xb_input(host,view,id,json);return;}\n  HRESULT code=S_OK;'),
        ('host->alive=false;host->views.clear();', 'host->alive=false;for(auto& entry:host->views){entry.second->closed=true;entry.second->dialog.reset();xb_dialog_cleanup(entry.second->window);if(entry.second->controller)entry.second->controller->Close();}host->views.clear();')
    ]
    for before, after in changes:
        if base.count(before) != 1:
            raise RuntimeError("Native hook no longer unique: " + before)
        base = base.replace(before, after, 1)
    target = NATIVE / "src/webview/xs_browser.inc.hpp"
    record_path = dev.ENV / "native-overlay.json"
    record = json.loads(record_path.read_text(encoding="utf-8")) if record_path.exists() else {}
    old = target.read_text(encoding="utf-8")
    pinned = (dev.SOURCE / "tools/native/xs_browser.inc.hpp").read_text(encoding="utf-8")
    if old not in (pinned, base) and hashlib.sha256(old.encode()).hexdigest() != record.get("browser"):
        raise RuntimeError("Unreviewed native browser edit preserved")
    helper = (dev.ROOT / "host/native-automation.inc.hpp").read_text(encoding="utf-8")
    helper_path = target.with_name("xs_browser_automation.inc.hpp")
    if helper_path.exists():
        current = helper_path.read_text(encoding="utf-8")
        if current != helper and hashlib.sha256(current.encode()).hexdigest() != record.get("helper"):
            raise RuntimeError("Unreviewed native helper edit preserved")
    target.write_text(base, encoding="utf-8", newline="\n")
    helper_path.write_text(helper, encoding="utf-8", newline="\n")
    hashes = {"browser": hashlib.sha256(base.encode()).hexdigest(), "helper": hashlib.sha256(helper.encode()).hexdigest()}
    record_path.write_text(json.dumps(hashes, indent=2) + "\n", encoding="utf-8")
    if args.prepare_only:
        print("Single native source prepared")
        return
    compiler = shutil.which("gcc") or "E:/software/w64devkit/bin/gcc.exe"
    env = dict(os.environ, XS_BUILD_COMMIT=revision)
    env["PATH"] = str(Path(compiler).parent) + os.pathsep + env.get("PATH", "")
    sys.path.insert(0, str(dev.SOURCE / "tools"))
    import build_mdo
    host = dev.ENV / "host/xs.exe"
    subprocess.run([sys.executable, "tools/build.py", *lock["xserver"]["required_extensions"],
                    "--build-dir", str(dev.ENV / "host/objects"), "--output", str(host),
                    "--cc", compiler, "--frontend", "native", "--gui-cc", compiler,
                    *build_mdo.host_profile_arguments(False), "--icon", str(build_mdo.ICON_PATH)],
                   cwd=NATIVE, env=env, check=True)
    receipt = {"binary_sha256": hashlib.sha256(host.read_bytes()).hexdigest(), "revision": revision,
               "sdk_patch": lock["sdk_overlay"]["sha256"], "overlay": hashes}
    (dev.ENV / "host/browser-use-build.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print("Reusable Browser Use native host built")


if __name__ == "__main__":
    main()
