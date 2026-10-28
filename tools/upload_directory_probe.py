"""Exercise actual directory File objects in the one project's native browser."""
import json
import sys

import dev
import native_probe


def main():
    native_probe.SCRIPT=(dev.ROOT/'tools/native_upload_directory_probe.js').read_text(encoding='utf-8')
    try:return native_probe.main(capture_evidence=False)
    finally:
        report=json.loads((dev.ENV/'page-observation-result.json').read_text(encoding='utf-8'))
        (dev.ENV/'native-upload-directory-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':sys.exit(main())
