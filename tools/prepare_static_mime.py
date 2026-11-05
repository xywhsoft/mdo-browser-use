"""Apply the reviewed MIME repair to the one pinned native build input."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import dev
from prepare_model_transport import safe_path, reviewed_source


def apply(check=False):
    python=shutil.which('python.exe') if os.name!='nt' and str(dev.ROOT).startswith('/mnt/') else None
    if python:
        script=subprocess.check_output(['wslpath','-w',str(Path(__file__).resolve())],text=True).strip()
        subprocess.run([python,script,*(['--check'] if check else [])],cwd=dev.ROOT,check=True);return
    if not check and dev.state().get('pid'):raise RuntimeError('Stop the original host before preparing native MIME')
    lock=json.loads((dev.SOURCE/'deps.lock').read_text(encoding='utf-8'));spec=lock['browser_use_static_mime']
    manifest=dev.SOURCE/spec['path'];safe_path(dev.SOURCE,manifest)
    if hashlib.sha256(manifest.read_bytes()).hexdigest()!=spec['sha256']:raise RuntimeError('MIME manifest identity changed')
    native=dev.ENV/'native-source'
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=native,text=True).strip()
    if revision!=lock['xserver']['commit']:raise RuntimeError('Native MIME baseline revision changed')
    record=json.loads(manifest.read_text(encoding='utf-8'))
    if record['path']!='src/protocol/http.h':raise RuntimeError('Unexpected native MIME target')
    target=native/record['path'];pending=target.with_name(target.name+'.mime.pending');safe_path(native,target);safe_path(native,pending)
    before=subprocess.check_output(['git','show','HEAD:'+record['path']],cwd=native).decode().replace('\r\n','\n')
    after=reviewed_source(record,before,manifest.parent);current=target.read_text(encoding='utf-8')
    if current not in (before,after) or pending.exists() or pending.is_symlink():raise RuntimeError('Unreviewed native MIME edit/staging preserved')
    if check and current!=after:raise RuntimeError('Prepare reviewed native MIME before building')
    if not check and current!=after:
        with pending.open('x',encoding='utf-8',newline='\n') as stream:stream.write(after)
        pending.replace(target)
    if not check:(dev.ENV/'native-static-mime.json').write_text(json.dumps({'revision':revision,'manifest_sha256':spec['sha256'],'after_sha256':record['after_sha256']},indent=2)+'\n')
    print('Reviewed static MIME '+('verified' if check else 'prepared'),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();apply(args.check)
