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
        ('struct xb_view{', (dev.ROOT / 'host/native-dialog-state.inc.hpp').read_text(encoding='utf-8')+'\n'+(dev.ROOT / 'host/native-popup-state.inc.hpp').read_text(encoding='utf-8')+'\nstruct xb_capture_job;\nstruct xb_drag_job;\nstruct xb_view{'),
        ('int errorCode=0;UINT64 navigation=0;', 'int errorCode=0;UINT64 navigation=0;int automationContext=0;bool closed=false,dialogsEnabled=false,popupsEnabled=false,requestedClose=false;std::shared_ptr<xb_dialog> dialog;std::weak_ptr<xb_input_sequence> input;std::map<std::string,bool> automationRequests;ICoreWebView2Environment* environment=nullptr;std::shared_ptr<xb_popup> opening;std::map<std::string,std::shared_ptr<xb_popup>> popups;std::string opener,popupError,popupTicket;'),
        ('~xb_view(){if(controller)', '~xb_view(){for(auto& item:popups)item.second->complete();if(opening)opening->complete();xb_popup_cleanup(window);if(environment)environment->Release();dialog.reset();xb_dialog_cleanup(window);if(controller)'),
        ('static void xb_receive(', '#include "xs_browser_automation.inc.hpp"\n#include "xs_browser_popups.inc.hpp"\n#include "xs_browser_capture.inc.hpp"\n#include "xs_browser_drag.inc.hpp"\nstatic void xb_receive('),
        ('active->loading=true;active->error.clear();', 'active->loading=true;active->automationContext=0;active->error.clear();'),
        ('std::weak_ptr<xb_view> weak=view;', 'xb_install_dialogs(host,view);\n            std::weak_ptr<xb_view> weak=view;'),
        ('if(!host->alive)return S_OK;', 'if(!host->alive||view->closed||host->views.find(browser)==host->views.end()||host->views.at(browser)!=view||(popup&&popup->completed)){if(host->views.count(browser)&&host->views.at(browser)==view)host->views.erase(browser);view->closed=true;host->reply(id,"","Browser creation was cancelled");return S_OK;}'),
        ('if(!host->alive||host->views.find(browser)==host->views.end()){if(controller)controller->Close();return S_OK;}', 'if(!host->alive||view->closed||host->views.find(browser)==host->views.end()||host->views.at(browser)!=view||(popup&&popup->completed)){if(controller)controller->Close();if(host->views.count(browser)&&host->views.at(browser)==view)host->views.erase(browser);view->closed=true;host->reply(id,"","Browser creation was cancelled");return S_OK;}'),
        ('      auto view=found->second;\n      if(view->controller)', '      auto view=found->second;\n      if(view->creating){host->reply(id,"","Browser creation is still in progress");return;}\n      if(view->controller)'),
        ('if(command=="close"){host->views.erase(browser);', 'if(command=="close"){if(found!=host->views.end()){found->second->closed=true;xb_cancel_popups(found->second);xb_abort_automation(host,found->second,false);found->second->dialog.reset();xb_dialog_cleanup(found->second->window);if(found->second->controller)found->second->controller->Close();if(found->second->window)ShowWindow(found->second->window,SW_HIDE);}host->views.erase(browser);'),
        ('const auto viewport=",\\"viewport_attached\\":', 'const auto viewport=",\\"navigation\\":"+xb_quote(std::to_string(view->navigation))+",\\"automation\\":true,\\"viewport_attached\\":'),
        ('+viewport+",\\"process_failed\\":', '+viewport+",\\"dialog_capability\\":"+(view->dialogsEnabled?"true":"false")+",\\"popup_capability\\":"+(view->popupsEnabled?"true":"false")+",\\"popup_open\\":"+(xb_popup_blocked(view)?"true":"false")+",\\"closed_by_page\\":"+(view->requestedClose?"true":"false")+",\\"opener\\":"+xb_quote(view->opener)+",\\"input_busy\\":"+(view->input.expired()?"false":"true")+",\\"dialog_open\\":"+(view->dialog?"true":"false")+",\\"process_failed\\":'),
        ('const bool mutation=command!="snapshot"&&command!="screenshot";', 'const bool mutation=command!="snapshot"&&command!="screenshot"&&!((command=="dialog"||command=="popups")&&xb_get(json,"action")=="list");'),
        ('  HRESULT code=S_OK;', '  if(command=="popups"){xb_popup_request(host,view,id,json);return;}\n  if(command=="dialog"){xb_dialog_request(host,view,id,json);return;}\n  if(command=="automation"){if(view->dialog){host->reply(id,"","A JavaScript dialog blocks page execution");return;}xb_automation(host,view,id,xb_get(json,"script"));return;}\n  if(command=="input"){xb_input(host,view,id,json);return;}\n  HRESULT code=S_OK;'),
        ('host->alive=false;host->views.clear();', 'host->alive=false;for(auto& entry:host->views){entry.second->closed=true;xb_cancel_popups(entry.second);entry.second->dialog.reset();xb_dialog_cleanup(entry.second->window);if(entry.second->controller)entry.second->controller->Close();}host->views.clear();'),
        ('    const auto url=xb_get(json,"url");const bool visible=xb_get(json,"visible")!="false";if(!xb_url(url)){host->reply(id,"","Only HTTP(S) navigation is supported");return;}', (dev.ROOT / 'host/native-popup-create.inc.hpp').read_text(encoding='utf-8').rstrip()),
        ('auto view=std::make_shared<xb_view>();view->bounds=xb_bounds(json);', 'auto view=std::make_shared<xb_view>();view->bounds=xb_bounds(json);if(popup){popup->claimed=true;view->opening=popup;view->opener=xb_get(json,"opener");view->popupTicket=popup->id;view->manual=opener->manual;}'),
        ('[host,view,id,browser,url,visible](HRESULT status', '[host,view,id,browser,url,visible,popup](HRESULT status'),
        ('[host,view,id,browser,url,visible](HRESULT code', '[host,view,id,browser,url,visible,popup](HRESULT code'),
        ('        auto* ready=xb_cb', '        view->environment=env;env->AddRef();\n        auto* ready=xb_cb'),
        ('      auto view=found->second;', '      auto view=found->second;\n      if(!xb_get(json,"popup").empty()&&(view->popupTicket!=xb_get(json,"popup")||view->opener!=xb_get(json,"opener"))){host->reply(id,"","Popup target browser already exists");return;}'),
        ('            HRESULT navigationResult=view->core->Navigate', '            if(popup){view->loading=url!="about:blank";HRESULT adopted=popup->complete(view->core);view->opening.reset();if(FAILED(adopted)){view->closed=true;view->controller->Close();host->views.erase(browser);host->reply(id,"","Cannot adopt the requested popup");return S_OK;}host->reply(id,"{\\"created\\":true,\\"popup\\":true}");return S_OK;}\n            HRESULT navigationResult=view->core->Navigate'),
        ('HRESULT result=host->loader.create_environment_with_options(nullptr,host->profile.c_str(),nullptr,environment);', 'HRESULT result=popup?environment->Invoke(S_OK,opener->environment):host->loader.create_environment_with_options(nullptr,host->profile.c_str(),nullptr,environment);')
    ]
    for before, after in changes:
        if base.count(before) != 1:
            raise RuntimeError("Native hook no longer unique: " + before)
        base = base.replace(before, after, 1)
    capture_hooks = [
        ('std::string opener,popupError,popupTicket;', 'std::string opener,popupError,popupTicket;std::shared_ptr<xb_capture_job> capture;std::shared_ptr<xb_drag_job> drag;'),
        ('bool alive=true;std::wstring', 'bool alive=true;unsigned captures=0;std::wstring'),
        ('found->second->closed=true;xb_cancel_popups', 'found->second->closed=true;xb_close_drag(found->second);xb_cancel_capture(found->second);xb_cancel_popups'),
        ('entry.second->closed=true;xb_cancel_popups', 'entry.second->closed=true;xb_close_drag(entry.second);xb_cancel_capture(entry.second);xb_cancel_popups'),
        ('if(command=="popups"){', 'if(command=="capture_cancel"){auto job=view->capture;if(job&&job->key==xb_get(json,"capture_key"))job->finish("","Screenshot was cancelled");host->reply(id,"{}");return;}\n  if(command=="capture"){xb_capture(host,view,id,json);return;}\n  if(command=="popups"){'),
        ('const bool mutation=command!="snapshot"', 'const bool mutation=command!="capture"&&command!="capture_cancel"&&command!="snapshot"'),
        ('\\"dialog_capability\\":', '\\"capture_capability\\":true,\\"advanced_mouse\\":true,\\"capture_busy\\":"+std::string(host->captures?"true":"false")+",\\"dialog_capability\\":'),
    ]
    for before, after in capture_hooks:
        if base.count(before) != 1:
            raise RuntimeError("Native capture hook no longer unique: " + before)
        base = base.replace(before, after, 1)
    drag_hooks = [
        ('if(command=="capture_cancel"){', 'if(command=="drag_cancel"){auto job=view->drag;if(job&&job->key==xb_get(json,"drag_key"))job->stop("Native drag was cancelled; inspect delivered effects");host->reply(id,"{}");return;}\n  if(command=="drag"){xb_drag(host,view,id,json);return;}\n  if(command=="capture_cancel"){'),
        ('const bool mutation=command!="capture"', 'const bool mutation=command!="drag_cancel"&&command!="capture"'),
        ('\\"advanced_mouse\\":true,', '\\"advanced_mouse\\":true,\\"drag_capability\\":true,\\"drag_cleanup_failed\\":"+std::string(view->drag&&view->drag->cleanupFailed?"true":"false")+",'),
        ('view->input.expired()?"false":"true"', 'view->input.expired()&&!view->drag?"false":"true"'),
    ]
    for before, after in drag_hooks:
        if base.count(before) != 1:
            raise RuntimeError("Native drag hook no longer unique: " + before)
        base = base.replace(before, after, 1)
    popup_start = base.index('            auto* popup=xb_cb<ICoreWebView2NewWindowRequestedEventHandler')
    popup_end = base.index('            if(!xb_place(view,visible))', popup_start)
    base = base[:popup_start] + '            xb_install_popups(host,view,browser);\n' + base[popup_end:]
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
    popups = (dev.ROOT / "host/native-popups.inc.hpp").read_text(encoding="utf-8")
    popup_path = target.with_name("xs_browser_popups.inc.hpp")
    if popup_path.exists():
        current = popup_path.read_text(encoding="utf-8")
        if current != popups and hashlib.sha256(current.encode()).hexdigest() != record.get("popups"):
            raise RuntimeError("Unreviewed native popup helper edit preserved")
    capture = (dev.ROOT / "host/native-capture.inc.hpp").read_text(encoding="utf-8")
    capture_path = target.with_name("xs_browser_capture.inc.hpp")
    if capture_path.exists():
        current = capture_path.read_text(encoding="utf-8")
        if current != capture and hashlib.sha256(current.encode()).hexdigest() != record.get("capture"):
            raise RuntimeError("Unreviewed native capture helper edit preserved")
    drag = (dev.ROOT / "host/native-drag.inc.hpp").read_text(encoding="utf-8")
    drag_path = target.with_name("xs_browser_drag.inc.hpp")
    if drag_path.exists():
        current = drag_path.read_text(encoding="utf-8")
        if current != drag and hashlib.sha256(current.encode()).hexdigest() != record.get("drag"):
            raise RuntimeError("Unreviewed native drag helper edit preserved")
    target.write_text(base, encoding="utf-8", newline="\n")
    helper_path.write_text(helper, encoding="utf-8", newline="\n")
    popup_path.write_text(popups, encoding="utf-8", newline="\n")
    capture_path.write_text(capture, encoding="utf-8", newline="\n")
    drag_path.write_text(drag, encoding="utf-8", newline="\n")
    hashes = {"browser": hashlib.sha256(base.encode()).hexdigest(), "helper": hashlib.sha256(helper.encode()).hexdigest(),
              "popups": hashlib.sha256(popups.encode()).hexdigest(),"capture": hashlib.sha256(capture.encode()).hexdigest(),"drag":hashlib.sha256(drag.encode()).hexdigest()}
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
