"""Run real native directory staging in the same deployment and restore headless."""
import json
import sys

import dev
import native_probe


def main():
    script=(dev.ROOT/'tools/native_upload_tree_probe.js').read_text(encoding='utf-8')
    if script.count('TREE_CACHE_ROOT')!=1:raise RuntimeError('Native tree observation placeholder changed')
    native_probe.SCRIPT=script.replace('TREE_CACHE_ROOT',json.dumps(str(dev.HOME/'data/cache/webview2-content/mdo-browser-use-transfers')))
    try:return native_probe.main(capture_evidence=False)
    finally:
        source=dev.ENV/'page-observation-result.json'
        if source.exists():
            report=json.loads(source.read_text(encoding='utf-8'))
            (dev.ENV/'native-upload-tree-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':sys.exit(main())
