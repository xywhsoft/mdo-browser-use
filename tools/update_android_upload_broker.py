"""Generate/check the private Android picker helper from one reviewed JS source.

The helper is native-controller-only. Generating it does not admit Android file
assignment or expose its nonce/object getter to Agent/UI commands.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'host/upload-picker-broker.js'
OUTPUT=ROOT/'host/android/MdoBrowserCdpUploadScripts.java'
EXPORT='export function uploadPickerBroker('


def installer(text):
    if '\x00' in text or len(text.encode('utf-8'))>32768 or text.count(EXPORT)!=1:
        raise RuntimeError('Private picker helper export/size changed')
    if len(re.findall(r'^\s*(?:import|export)\s',text,re.MULTILINE))!=1 or not text.rstrip().endswith('}'):
        raise RuntimeError('Private picker helper must remain one self-contained function')
    return '(function(){\n'+text.replace(EXPORT,'function uploadPickerBroker(',1).strip()+'\nreturn uploadPickerBroker();})()'


def render(text):
    body=installer(text);digest=hashlib.sha256(text.encode('utf-8')).hexdigest()
    return '\n'.join(['package org.xserver.android;','',
        '/** Generated from host/upload-picker-broker.js by tools/update_android_upload_broker.py.',
        ' * Native-controller-only; nonce/node must never enter Agent/UI results.',
        ' * Source SHA-256: '+digest+' */','final class MdoBrowserCdpUploadScripts {',
        '    static final String INSTALL='+json.dumps(body,ensure_ascii=True)+';',
        '    private MdoBrowserCdpUploadScripts(){}','}',''])


def verify():
    if SOURCE.is_symlink() or OUTPUT.is_symlink():raise RuntimeError('Linked private picker helper preserved')
    wanted=render(SOURCE.read_text(encoding='utf-8'))
    if not OUTPUT.is_file() or OUTPUT.read_text(encoding='utf-8')!=wanted:
        raise RuntimeError('Android picker helper is stale; review and run tools/update_android_upload_broker.py')


def main(check=False):
    if check:verify();print('Private Android picker helper matches canonical JavaScript');return
    if SOURCE.is_symlink() or OUTPUT.is_symlink():raise RuntimeError('Linked private picker helper preserved')
    wanted=render(SOURCE.read_text(encoding='utf-8'))
    if OUTPUT.exists() and OUTPUT.read_text(encoding='utf-8')!=wanted:
        result=subprocess.run(['git','show','HEAD:host/android/MdoBrowserCdpUploadScripts.java'],cwd=ROOT,capture_output=True)
        if result.returncode or OUTPUT.read_bytes()!=result.stdout.replace(b'\r\n',b'\n'):
            raise RuntimeError('Unreviewed generated picker helper edit preserved')
    pending=OUTPUT.with_suffix('.picker.pending')
    if pending.exists() or pending.is_symlink():raise RuntimeError('Interrupted picker helper generation preserved')
    pending.write_text(wanted,encoding='utf-8',newline='\n');pending.replace(OUTPUT);verify()
    print('Generated private Android picker helper')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    try:main(args.check)
    except (RuntimeError,OSError,ValueError) as error:print(error,file=sys.stderr);raise SystemExit(1)
