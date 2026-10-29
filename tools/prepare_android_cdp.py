"""Prepare reviewed private JNI files in the existing pinned native checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
NATIVE=ROOT/'.mdo/native-source'
FILES=('MdoBrowserCdpSocket.h','MdoBrowserCdpSocket.c','MdoBrowserCdpWebSocket.h','MdoBrowserCdpWebSocket.c','MdoBrowserCdpJni.inc.c')


def digest(text):return hashlib.sha256(text.encode('utf-8')).hexdigest()


def apply(check=False):
    # This shared checkout's object alternates use Windows paths. Read its
    # pinned objects with Windows Git; never clone/rewrite the object store.
    python=shutil.which('python.exe') if os.name!='nt' and str(ROOT).startswith('/mnt/') else None
    if python:
        script=subprocess.check_output(['wslpath','-w',str(Path(__file__).resolve())],text=True).strip()
        subprocess.run([python,script,*(['--check'] if check else [])],cwd=ROOT,check=True);return
    lock=json.loads((ROOT/'.mdo/source/deps.lock').read_text(encoding='utf-8'))
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=NATIVE,text=True).strip()
    if revision!=lock['xserver']['commit']:raise RuntimeError('Native source revision changed; it is preserved')
    record=ROOT/'.mdo/native-cdp-overlays.json'
    old=json.loads(record.read_text(encoding='utf-8')) if record.is_file() else {}
    previous=old.get('files',{}) if old.get('baseline')==revision else {}
    relative='android/native/host_jni.c';target=NATIVE/relative
    baseline=subprocess.check_output(['git','show','HEAD:'+relative],cwd=NATIVE).decode('utf-8').replace('\r\n','\n')
    anchor='#include "src/sdk/xsbase.h"'
    if baseline.count(anchor)!=1:raise RuntimeError('Pinned JNI entry changed')
    wanted=baseline.replace(anchor,anchor+'\n#include "MdoBrowserCdpJni.inc.c"')
    staged={target:wanted}
    for name in FILES:staged[NATIVE/'android/native'/name]=(ROOT/'host/android'/name).read_text(encoding='utf-8')
    for path,text in staged.items():
        if not path.resolve().is_relative_to(NATIVE.resolve()):raise RuntimeError('Native transport target escaped the deployment')
        for ancestor in (path,*path.parents):
            if ancestor.is_symlink() or ancestor.exists() and getattr(ancestor.lstat(),'st_file_attributes',0)&0x400:
                raise RuntimeError('Native source link/reparse point preserved')
            if ancestor==NATIVE:break
        if path.exists() and not path.is_file():raise RuntimeError('Non-regular native transport target preserved')
        current=path.read_text(encoding='utf-8') if path.is_file() else None
        allowed=(baseline,text) if path==target else (None,text)
        if current not in allowed and (current is None or previous.get(path.relative_to(NATIVE).as_posix())!=digest(current)):
            raise RuntimeError('Unreviewed native transport edit preserved: '+path.name)
        if check and current!=text:raise RuntimeError('Run tools/prepare_android_cdp.py before building the native host')
        pending=path.with_name(path.name+'.cdp.pending')
        if not check and (pending.exists() or pending.is_symlink()):raise RuntimeError('Interrupted native transport staging file preserved')
    pending_record=record.with_name(record.name+'.pending')
    if not check and (pending_record.exists() or pending_record.is_symlink()):raise RuntimeError('Interrupted native transport receipt preserved')
    if not check:
        for path,text in staged.items():
            pending=path.with_name(path.name+'.cdp.pending');pending.write_text(text,encoding='utf-8',newline='\n');pending.replace(path)
        pending_record.write_text(json.dumps(dict(baseline=revision,files={p.relative_to(NATIVE).as_posix():digest(v) for p,v in staged.items()}),indent=2)+'\n',encoding='utf-8')
        pending_record.replace(record)
    print('Reviewed Android JNI transport '+('verified' if check else 'prepared'),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    try:apply(args.check)
    except (RuntimeError,OSError,ValueError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
