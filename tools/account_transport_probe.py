"""Fault the exact read-recovery C fragment using existing WSL gcc, no app copy."""
import hashlib
import json
import subprocess
import sys
import dev


def main():
    output=dev.ENV/'qa/account-transport';output.mkdir(parents=True,exist_ok=True)
    if output.is_symlink() or not output.resolve().is_relative_to(dev.ENV.resolve()):
        raise RuntimeError('Linked transport output preserved')
    def linux(path):
        value=path.resolve().as_posix();return '/mnt/'+value[0].lower()+value[2:]
    source=dev.ROOT/'tests/fixtures/account-fetch-contract.c';binary=output/'contract'
    subprocess.run(['wsl.exe','--exec','gcc','-std=c11','-Wall','-Wextra','-Werror',linux(source),'-o',linux(binary)],
        check=True,capture_output=True,text=True,timeout=30)
    run=subprocess.run(['wsl.exe','--exec',linux(binary)],check=True,capture_output=True,text=True,timeout=10)
    report=json.loads(run.stdout)
    report['scope']='actual-product-fetch-policy-with-controlled-clock-and-network'
    report['source_sha256']={name:hashlib.sha256((dev.ROOT/name).read_bytes()).hexdigest() for name in
        ('host/account-fetch.inc.c','host/account-transport.h','tests/fixtures/account-fetch-contract.c','tools/account_transport_probe.py')}
    (dev.ENV/'account-transport-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as error:print((error.stderr or '')[-2400:],file=sys.stderr);raise SystemExit(1)
    except (OSError,ValueError,RuntimeError,subprocess.SubprocessError) as error:print(error,file=sys.stderr);raise SystemExit(1)
