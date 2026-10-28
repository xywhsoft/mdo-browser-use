"""Real Chromium codecs and production shared capture execution, synthetic pixels.

Reuses the existing native probe window, Home, fixture page and output paths.
No model, external page, private content or extra application copy is used.
"""
import shutil
import sys
import native_probe


def main():
    source=native_probe.SCRIPT
    start=source.index('try {\n  const raceBrowser=');end=source.index('} catch(error) { report=')
    helper=(native_probe.ROOT/'tools/capture_privacy_probe.js').read_text(encoding='utf-8')
    script=source[:start]+helper+'\ntry { report=await runCapturePrivacyProbe({assert});\n'+source[end:]
    try:return native_probe.main(script_override=script,download_fixture=False,capture_evidence=False)
    finally:
        result=native_probe.dev.ENV/'page-observation-result.json'
        if result.is_file():shutil.copyfile(result,native_probe.dev.ENV/'capture-privacy-result.json')


if __name__=='__main__':
    try:raise SystemExit(main())
    except (RuntimeError,OSError,ValueError) as error:print(error,file=sys.stderr);raise SystemExit(1)
