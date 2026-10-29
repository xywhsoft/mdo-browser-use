"""Bounded acquisition of the SDK lock's archives; never execute downloads here."""
import hashlib
import os
from pathlib import Path
import stat
import time
from urllib.parse import urlsplit
import urllib.request
import zipfile


def hashes(path):
    sha1,sha256=hashlib.sha1(),hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda:source.read(1024*1024),b''):
            sha1.update(chunk);sha256.update(chunk)
    return sha1.hexdigest(),sha256.hexdigest()


def regular_bytes(root):
    # WSL links can appear as regular reparse files to Windows Python. Never
    # open/follow them or traverse directory junctions for disk accounting.
    total=0;pending=[root]
    while pending:
        with os.scandir(pending.pop()) as entries:
            for entry in entries:
                info=entry.stat(follow_symlinks=False)
                if stat.S_ISLNK(info.st_mode) or getattr(info,'st_file_attributes',0)&0x400:continue
                if stat.S_ISDIR(info.st_mode):pending.append(entry.path)
                elif stat.S_ISREG(info.st_mode):total+=info.st_size
    return total


def archive(root,row,expanded_limit,opener=None):
    url=urlsplit(row['url']);name=Path(url.path).name
    if (url.scheme!='https' or url.hostname!='dl.google.com' or url.username or url.password or url.query or url.fragment
            or not name.endswith('.zip') or type(row['size']) is not int or row['size']<1):
        raise RuntimeError('Unexpected pinned Android package URL/size')
    cache=root/'downloads';cache.mkdir(exist_ok=True)
    if cache.is_symlink() or not cache.resolve().is_relative_to(root.resolve()):raise RuntimeError('Linked Android archive cache preserved')
    target=cache/name;pending=target.with_suffix('.partial')
    downloaded=0
    if target.is_symlink() or pending.exists() or pending.is_symlink():raise RuntimeError('Unknown Android archive/partial preserved')
    if not target.exists():
        key=None;started=last=time.monotonic();count=0
        try:
            with (opener or urllib.request.urlopen)(row['url'],timeout=60) as response:
                declared=response.headers.get('Content-Length')
                if declared is not None and int(declared)!=row['size']:raise RuntimeError('Pinned Android archive length changed')
                with pending.open('xb') as output:
                    info=os.fstat(output.fileno());key=(info.st_dev,info.st_ino)
                    while True:
                        chunk=response.read(min(1024*1024,row['size']-count+1))
                        if not chunk:break
                        if len(chunk)>row['size']-count:raise RuntimeError('Pinned Android archive exceeded its download bound')
                        output.write(chunk);count+=len(chunk)
                        if time.monotonic()-last>=20:
                            print(f'  {name}: {count//1048576}/{row["size"]//1048576} MiB',flush=True);last=time.monotonic()
                info=pending.lstat()
                if not stat.S_ISREG(info.st_mode) or (info.st_dev,info.st_ino)!=key:raise RuntimeError('Android download identity changed')
                if count!=row['size'] or hashes(pending)[0]!=row['sha1']:raise RuntimeError('Pinned Android archive checksum/size mismatch')
                # Same-directory hard-link publication refuses an existing target.
                os.link(pending,target)
                downloaded=count
            print(f'Verified {name} ({time.monotonic()-started:.1f}s)',flush=True)
        finally:
            if key is not None and pending.exists():
                info=pending.lstat()
                if stat.S_ISREG(info.st_mode) and (info.st_dev,info.st_ino)==key:pending.unlink()
    if not target.is_file() or target.stat().st_size!=row['size']:raise RuntimeError('Unexpected Android archive preserved')
    sha1,sha256=hashes(target)
    if sha1!=row['sha1']:raise RuntimeError('Unexpected Android archive checksum preserved')
    with zipfile.ZipFile(target) as source:
        entries=source.infolist();expanded=sum(item.file_size for item in entries)
        if len(entries)>200000 or expanded>expanded_limit:raise RuntimeError('Pinned Android archive exceeds the extraction budget')
    return target,dict(archive_sha256=sha256,expanded_bytes=expanded,entries=len(entries),downloaded_bytes=downloaded)
