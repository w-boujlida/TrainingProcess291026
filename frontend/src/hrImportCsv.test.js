import test from 'node:test';
import assert from 'node:assert/strict';
import { credentialsCsv } from './hrImportCsv.js';

test('credentials retain leading zeros and safely quote names, delimiters and passwords', () => {
  const result = credentialsCsv([{ username: '0012', fullName: 'Test;"quoted"', active: false, temporaryPassword: 'Aa2!;safe' }]);
  assert.ok(result.startsWith('\uFEFF'));
  assert.ok(result.includes('"0012";"Test;""quoted""";"Non";"Aa2!;safe"'));
});
test('spreadsheet formula prefixes are escaped as text in every exported field', () => {
  const result = credentialsCsv([{ username: '-12', fullName: '=1+1', active: true, temporaryPassword: '@test' }]);
  assert.ok(result.includes(`"'-12";"'=1+1";"Oui";"'@test"`));
});
