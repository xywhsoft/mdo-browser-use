"""Install, fingerprint-update and remove the real ZIPs in an existing mdo.

The two target directories must initially be absent. Native code stays disabled;
the separate native package probe checks actual loading and prompt composition.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.error
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--url',required=True);p.add_argument('--home',type=Path,required=True)
    a=p.parse_args();checks={};owned={};tasks=[]
    targets=[('skills','browser-use'),('plugins','mdo.browser-use')]
    for kind,identity in targets:
        assert not (a.home/kind/identity).exists(),'Preserving existing extension: '+identity

    def request(method,path,value=None):
        headers={}
        if method!='GET':
            with urllib.request.urlopen(a.url+'/api/v1/bootstrap',timeout=15) as r:
                headers['X-Mdo-Write-Token']=r.headers.get('X-Mdo-Write-Token','')
        data=value if isinstance(value,bytes) else json.dumps(value).encode() if value is not None else None
        if data is not None:headers['Content-Type']='application/octet-stream' if isinstance(value,bytes) else 'application/json'
        req=urllib.request.Request(a.url+path,data=data,headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,timeout=120) as r:result=json.load(r)
        except urllib.error.HTTPError as e:
            raise AssertionError((e.code,json.load(e))) from e
        assert result.get('ok'),result
        return result['data']

    def verify(kind,identity,data):
        root=a.home/kind/identity
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for item in z.infolist():
                if not item.is_dir():assert (root/item.filename).read_bytes()==z.read(item),item.filename

    def install(kind,identity,data,revision):
        task=request('POST','/api/v1/extension-tasks',dict(kind=kind,purpose='install',bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
        path='/api/v1/extension-tasks/'+task['id'];tasks.append(path)
        request('PUT',path+'/chunks/0',data);request('POST',path+'/seal',{})
        request('POST',path+'/install',dict(id=identity,expected_revision=revision,trust_code=True,enabled=False))
        owned[(kind,identity)]=True
        row=request('GET',f'/api/v1/extensions/{kind}/{identity}')
        assert row.get('id')==('plugin:mdo.browser-use' if kind=='plugins' else 'skill:mdo.browser-use')
        assert row.get('revision')
        verify(kind,identity,data)
        return row['revision']

    try:
        for kind,identity in targets:
            filename='mdo-browser-use-0.1.0.zip' if kind=='plugins' else 'mdo-browser-use-skill-0.1.0.zip'
            data=(ROOT/'dist'/filename).read_bytes()
            revision=install(kind,identity,data,'');checks[kind+'_install_and_bytes']=True
            with zipfile.ZipFile(io.BytesIO(data)) as z:files={i.filename:z.read(i) for i in z.infolist()}
            meta=json.loads(files['extension.json']);meta['version']='0.1.1'
            files['extension.json']=json.dumps(meta).encode();files['qa-update.txt']=b'Owned lifecycle fixture only.\n'
            out=io.BytesIO()
            with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
                for name,body in files.items():z.writestr(name,body)
            updated=install(kind,identity,out.getvalue(),revision)
            assert updated!=revision;checks[kind+'_fingerprint_update']=True
        for kind,identity in reversed(targets):
            row=request('GET',f'/api/v1/extensions/{kind}/{identity}')
            request('DELETE',f'/api/v1/extensions/{kind}/{identity}',dict(expected_revision=row['revision']))
            assert not (a.home/kind/identity).exists();owned.pop((kind,identity))
            checks[kind+'_uninstall']=True
    finally:
        for path in tasks:request('DELETE',path)
        # Failed checks retain installed evidence instead of deleting by path.
        out=ROOT/'.mdo/probes';out.mkdir(parents=True,exist_ok=True)
        (out/'directory-package-lifecycle.json').write_text(json.dumps(dict(checks=checks,retained=list(owned)),indent=2),encoding='utf-8')
    assert len(checks)==6,checks
    print(json.dumps(checks))


if __name__=='__main__':main()
