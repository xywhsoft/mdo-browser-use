"""Compile the production C bridge as a bounded native QA client, no app copy."""
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys

import dev


def main():
    native=dev.ENV/'native-source'
    root=dev.ENV/'qa/remote-bridge';root.mkdir(parents=True,exist_ok=True)
    compiler=Path('E:/software/w64devkit/bin/gcc.exe')
    source=dev.ROOT/'tests/fixtures/remote-bridge.c'
    binary=root/'remote-bridge.exe'
    env=dict(os.environ);env['PATH']=str(compiler.parent)+os.pathsep+env.get('PATH','')
    includes=[native/'lib',native/'tcc',native/'src/sdk',dev.SOURCE/'app/src']
    includes += [native/'lib'/name for name in ('xwork','xllm','xllm-session','xoauth2')]
    command=[str(compiler),'-std=c11','-O1','-ffunction-sections','-fdata-sections','-Wl,--gc-sections']
    command += [option for path in includes for option in ('-I',str(path))]
    # Reuse one ordinary component object; no application/native source copy.
    # Keep the cache bound to exact header/compiler/flags and object bytes.
    runtime=root/'xrt.c';runtime.write_text('#define XRT_MODULE_ALL\n#define XRT_IMPLEMENTATION\n#include <xrt.h>\n',encoding='utf-8')
    obj=root/'xrt.o';cache=root/'xrt-build.json'
    inputs={'header_sha256':hashlib.sha256((native/'lib/xrt.h').read_bytes()).hexdigest(),
            'compiler_sha256':hashlib.sha256(compiler.read_bytes()).hexdigest(),'flags':command,
            'source_sha256':hashlib.sha256(runtime.read_bytes()).hexdigest()}
    saved=json.loads(cache.read_text()) if cache.is_file() else {}
    if saved.get('inputs')!=inputs or not obj.is_file() or saved.get('object_sha256')!=hashlib.sha256(obj.read_bytes()).hexdigest():
        pending=obj.with_suffix('.pending.o')
        subprocess.run(command+['-c',str(runtime),'-o',str(pending)],cwd=dev.ROOT,env=env,check=True)
        pending.replace(obj)
        cache.write_text(json.dumps({'inputs':inputs,'object_sha256':hashlib.sha256(obj.read_bytes()).hexdigest()},indent=2)+'\n',encoding='utf-8')
    dependencies=root/'bridge.d'
    command += ['-MMD','-MF',str(dependencies),'-MT','qa_bridge',str(source),str(obj),'-o',str(binary),'-lws2_32','-lshell32','-liphlpapi','-ladvapi32']
    subprocess.run(command,cwd=dev.ROOT,env=env,check=True)
    names=shlex.split(dependencies.read_text().replace('\\\n',' ').partition(':')[2],posix=False)
    sources={}
    for name in names:
        path=Path(name).resolve();relative=path.relative_to(dev.ROOT).as_posix()
        sources[relative]=hashlib.sha256(path.read_bytes()).hexdigest()
    receipt={'format':'mdo.browser-use.native-bridge-qa.v1','binary':str(binary.relative_to(dev.ROOT)),
             'bytes':binary.stat().st_size,'sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),
             'fixture_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
             'native_revision':subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip(),
             'app_revision':dev.LOCK['commit'],'sources':sources,
             'application_copies':0,'account_authentication_verified':False}
    (root/'build.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(receipt,indent=2))


if __name__=='__main__':main()
