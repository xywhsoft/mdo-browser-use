"""Mounted control toolbar in the one normal Windows development deployment."""
import hashlib
import json
from pathlib import Path
import sys

import dev
import native_probe


def main():
    output = dev.ENV / 'control-ui-result.json'
    output.write_text('{"passed":false,"state":"running"}\n', encoding='utf-8')
    source = dev.ROOT / 'tools/control_ui_probe.js'
    native_probe.main(script_override=source.read_text(encoding='utf-8'), download_fixture=False)
    report = json.loads((dev.ENV / 'page-observation-result.json').read_text(encoding='utf-8'))
    if not report['passed'] or dev.state()['window']:
        raise RuntimeError('Control UI did not pass and restore the normal headless host')
    report['headless_restored'] = True
    report['source_sha256'] = {str(p.relative_to(dev.ROOT)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in (source, Path(__file__), dev.ROOT / 'host/browser-view.js')}
    report['deployed_styles_sha256'] = {p.relative_to(dev.ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                                       for p in (dev.SOURCE / 'app/web/css/app.css', dev.SOURCE / 'app/web/css/workbench.css')}
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)
