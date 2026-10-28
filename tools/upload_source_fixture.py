"""One shared synthetic source tree; preserve unknown entries and source data."""
import hashlib
from pathlib import Path

import dev


def source_tree():
    root=dev.ENV/'qa/public-upload-directory'
    parent=dev.ENV/'qa'
    if parent.is_symlink() or parent.is_junction() or not parent.resolve().is_relative_to(dev.ENV.resolve()):raise RuntimeError('Unknown QA parent preserved')
    if root.resolve().parent!=(dev.ENV/'qa').resolve():raise RuntimeError('Directory fixture escaped project QA')
    files={'nested/same.bin':bytes(i%251 for i in range(131075)),'other/same.bin':b'distinct',
           'zero.txt':b'','nested/'+'n'*240:b'long directory leaf'}
    def native(path):
        if not path.absolute().is_relative_to(dev.ENV):raise RuntimeError('Fixture path escaped project deployment')
        return Path('\\\\?\\'+str(path.absolute()))
    directories=[root,root/'nested',root/'nested/empty',root/'other',root/'空目录']
    for path in directories:
        target=native(path)
        if target.is_symlink() or target.is_junction():raise RuntimeError('Unknown fixture reparse point preserved')
        target.mkdir(parents=True,exist_ok=True)
    for relative,data in files.items():
        path=native(root/relative)
        if path.is_symlink() or path.is_junction() or path.exists() and (not path.is_file() or path.read_bytes()!=data):
            raise RuntimeError('Unknown source fixture data preserved')
        if not path.exists():path.write_bytes(data)
    entries=[*directories[1:],*(root/name for name in files)]
    for directory in directories:
        expected={path.name for path in entries if path.parent==directory}
        if {path.name for path in native(directory).iterdir()}!=expected:raise RuntimeError('Unknown directory fixture entry preserved')
    return root,{relative:{'size':len(data),'sha256':hashlib.sha256(data).hexdigest()} for relative,data in files.items()}
