import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import test from 'node:test';

test('all applied frontend modules parse with their original imports intact', () => {
  const root = fileURLToPath(new URL('../.mdo/source/app/web/js/', import.meta.url));
  const visit = directory => readdirSync(directory, {withFileTypes:true}).sort((a,b) => a.name.localeCompare(b.name))
    .flatMap(row => row.isDirectory() ? visit(join(directory,row.name)) : row.name.endsWith('.js') ? [join(directory,row.name)] : []);
  const files = visit(root); assert.ok(files.length > 0);
  for (const path of files) {
    const parsed = spawnSync(process.execPath, ['--input-type=module','--check'], {
      input:readFileSync(path,'utf8'), encoding:'utf8', timeout:10000});
    assert.equal(parsed.status, 0, `${path}: ${parsed.stderr}`);
  }
});
