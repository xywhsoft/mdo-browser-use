"""Deterministic lifetime, replacement and OOM gates for the production C fragment."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class DirectorySnapshotFaultTests(unittest.TestCase):
    def test_all_allocation_positions_and_scanning_faults_release_every_owner(self):
        compiler=Path('E:/software/w64devkit/bin/gcc.exe')
        output=dev.ENV/'qa';output.mkdir(exist_ok=True);binary=output/'file-tree-faults.exe'
        built=subprocess.run([str(compiler),'-std=c11','-O2','-Wall','-Wextra',
            str(dev.ROOT/'tests/fixtures/file-tree-faults.c'),'-o',str(binary)],capture_output=True,text=True)
        self.assertEqual(built.returncode,0,built.stderr)
        result=subprocess.run([str(binary)],capture_output=True,text=True,
            env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')))
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('15 replacement/cancellation/cleanup faults',result.stdout)
        (dev.ENV/'directory-snapshot-faults.txt').write_text(result.stdout,encoding='utf-8')


if __name__=='__main__':unittest.main()
