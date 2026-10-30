"""Qualify original root execution worlds in the single real WebView2 host."""
import sys
import native_probe
import dev


if __name__=='__main__':
    try:
        source=(dev.ROOT/'tools/native_worlds_probe.js').read_text(encoding='utf-8')
        raise SystemExit(native_probe.main(script_override=source,capture_evidence=False,download_fixture=False))
    except (RuntimeError,OSError,ValueError) as error:
        print(error,file=sys.stderr);raise SystemExit(1)
