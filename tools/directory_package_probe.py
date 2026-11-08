"""Verify current deterministic ZIPs and their actual installed directory bytes."""
import hashlib
import io
import json
from pathlib import Path
import zipfile
import dev
import package_plugin


def main():
    checks = []
    for kind, identity, logical, build in (
        ('skills', 'browser-use', 'skill:mdo.browser-use', package_plugin.build_skill_directory),
        ('plugins', 'mdo.browser-use', 'plugin:mdo.browser-use', package_plugin.build_directory),
    ):
        name, data, receipt = build()
        assert name.endswith('.zip') and Path(name).name == name
        assert build() == (name, data, receipt)
        assert receipt['artifact']['sha256'] == hashlib.sha256(data).hexdigest()
        artifact = dev.ROOT/'dist'/name
        assert artifact.read_bytes() == data
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            meta = json.loads(archive.read('extension.json'))
            assert meta['id'] == logical and meta['kind'] == kind
            code, _, raw = dev.request('GET', '/api/v1/extensions/'+kind+'/'+identity)
            installed = json.loads(raw)['data']
            assert code == 200 and installed['valid'] and installed['enabled'] and installed['id'] == logical
            if kind == 'plugins':
                plugin = dev.call('plugin.get', {'id':logical})
                expected = {r['path']:r for r in receipt['files']}
                assert {r['path'] for r in plugin['files'] if not r.get('directory')} == set(expected)
                for path, row in expected.items():
                    result = dev.call('plugin.file.read', {'id':logical, 'revision':plugin['revision'], 'path':path})
                    content = result['text'].encode('utf-8')
                    assert result['revision'] == plugin['revision'] and content == archive.read(path)
                    assert len(content) == row['bytes'] and hashlib.sha256(content).hexdigest() == row['sha256']
                assert meta['dependencies'] == [{'id':'skill:mdo.browser-use'}]
            else:
                root = dev.HOME/'skills'/identity
                for path in archive.namelist():
                    assert (root/path).read_bytes() == archive.read(path)
        checks.extend([kind+'_deterministic_zip',kind+'_manifest_and_identity',kind+'_installed_files_match'])
    report = dict(passed=True,checks=checks,real_model_calls=0,same_home=True)
    (dev.ENV/'directory-package-result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))


if __name__ == '__main__':main()
