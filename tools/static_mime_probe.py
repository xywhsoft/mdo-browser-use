"""Compile the exact pinned static MIME function and its reviewed repair."""
import hashlib
import json
import subprocess
import sys
import dev
from prepare_model_transport import reviewed_source


def main():
    manifest=dev.ROOT/'host/static-mime.json';record=json.loads(manifest.read_text(encoding='utf-8'))
    before=subprocess.check_output(['git','show','HEAD:'+record['path']],cwd=dev.ENV/'native-source').decode().replace('\r\n','\n')
    after=reviewed_source(record,before,manifest.parent);out=dev.ENV/'qa/static-mime';out.mkdir(parents=True,exist_ok=True)
    if out.is_symlink() or not out.resolve().is_relative_to(dev.ENV.resolve()):raise RuntimeError('Linked MIME output preserved')
    checks='''
int main(void){
 const char* paths[]={"/plugin/client.mjs","/plugin/Contracts.MJS","bundle.js","view.css","settings.json","image.png","payload.bin","without-extension","module.mjs.bin"};
 const char* types[]={"text/javascript; charset=utf-8","text/javascript; charset=utf-8","text/javascript; charset=utf-8","text/css; charset=utf-8","application/json; charset=utf-8","image/png","application/octet-stream","application/octet-stream","application/octet-stream"};
 for(size_t i=0;i<sizeof(paths)/sizeof(paths[0]);i++)if(strcmp(XS_HttpMime(paths[i]),types[i]))return 3;
 puts("mime checks passed");return 0;}
'''
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    results=[]
    for name,text in [('before',before),('after',after)]:
        start=text.index('static const char* XS_HttpMime(const char* sName)\n{');end=text.index('\n/* host ',start)
        source=out/'contract.c';source.write_text('#include <stddef.h>\n#include <string.h>\n#include <stdio.h>\n'+text[start:end]+checks,encoding='utf-8')
        binary=out/'contract'
        subprocess.run(['wsl.exe','--exec','gcc','-std=c11','-Wall','-Wextra','-Werror',linux(source),'-o',linux(binary)],check=True,capture_output=True,text=True,timeout=30)
        run=subprocess.run(['wsl.exe','--exec',linux(binary)],capture_output=True,text=True,timeout=10)
        results.append(run.returncode)
        (dev.ENV/('E263-mime-'+name+'.log')).write_text(json.dumps({'exit_code':run.returncode,'scope':'exact-native-static-mime-function'})+'\n')
    if results!=[3,0]:raise RuntimeError('MIME red/green qualification did not match')
    report={'passed':True,'checks':9,'old_mjs_rejected':True,'source_sha256':record['after_sha256'],'real_network_calls':0}
    (dev.ENV/'static-mime-result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-1800:],file=sys.stderr);raise SystemExit(1)
