"""Run the complete native file-input lifecycle in the existing mdo/Home."""
import sys
import native_probe


if __name__=='__main__':
    try:
        source=native_probe.SCRIPT
        start=source.index('try {\n  const raceBrowser=')
        end=source.index('} catch(error) { report=')
        source=source[:start]+'try { await runFileInputProbe({bridge,assert,pause,runtimeSource}); report={passed:true,checks,backend:"actual-webview2-file-input",native_upload_staging:true,native_file_inputs:true,native_file_clear:true};\n'+source[end:]
        raise SystemExit(native_probe.main(script_override=source,capture_evidence=False,download_fixture=False))
    except (RuntimeError,OSError,ValueError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
