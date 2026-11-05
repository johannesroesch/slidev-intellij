'use strict'
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const { spawnSync } = require('node:child_process')
const resource = path.join(__dirname, '../src/main/resources/dev/slidev/intellij/check-expressions.cjs')
const { loadCompiler, check } = require(resource)
const entry = path.resolve(process.argv[2] || path.join(__dirname, '../examples/slides.md'))
const compiler = loadCompiler(entry)
assert(compiler, 'Install @vue/compiler-dom in the fixture project, or pass a Slidev entry path with installed dependencies.')
const valid = [
  '{{ 1 + 2 }}', '{{ name?.length ?? 0 }}', '{{ value as string }}',
  '{{ `Hello ${name}` }}', '<div :title="`Hello ${name}`" />',
  '<div v-click="3">X</div>', '<div v-for="(item, index) in items">{{ item }}</div>',
  '<button @click="count++; go()">X</button>', '<Component><template #default="{ item }">{{ item }}</template></Component>',
  '<div v-pre>{{ 1 + }}</div>',
]
for (const source of valid) assert.deepEqual(check(source, compiler), [], source)
for (const source of [
  '{{ 1 + }}', '<div v-click="1 +">X</div>', '<div :class="{ bad: }" />',
  '<button @click="count +">X</button>', '<div v-for="item items">X</div>',
  '{{ missing',
]) {
  const errors = check(source, compiler)
  assert(errors.length > 0, source)
  for (const error of errors) assert(error.start >= 0 && error.end >= error.start && error.end <= source.length)
}
const script = fs.readFileSync(resource, 'utf8')
const result = spawnSync(process.execPath, ['-e', script, entry], { input: '{{ 1 + }}', encoding: 'utf8', timeout: 5000 })
assert.equal(result.status, 0, result.stderr)
assert.match(result.stdout, /slidev-expression\tready/)
assert.match(result.stdout, /slidev-expression\terror\t3\t3\t/)
const isolated = fs.mkdtempSync(path.join(os.tmpdir(), 'slidev-pnpm-check-'))
try {
  const store = path.join(isolated, 'store/node_modules')
  const cli = path.join(store, '@slidev/cli')
  fs.mkdirSync(path.join(cli, 'bin'), { recursive: true })
  fs.writeFileSync(path.join(cli, 'bin/slidev.mjs'), '')
  const compilerPath = require('node:module').createRequire(entry).resolve('@vue/compiler-dom/package.json')
  fs.mkdirSync(path.join(store, '@vue'), { recursive: true })
  fs.symlinkSync(path.dirname(compilerPath), path.join(store, '@vue/compiler-dom'), 'dir')
  const project = path.join(isolated, 'project')
  fs.mkdirSync(path.join(project, 'node_modules/@slidev'), { recursive: true })
  fs.symlinkSync(cli, path.join(project, 'node_modules/@slidev/cli'), 'dir')
  const indirect = loadCompiler(path.join(project, 'slides.md'))
  assert(indirect, 'Resolve compiler through the real pnpm CLI path')
  assert.equal(check('{{ 1 + }}', indirect).length, 1)
  const missing = spawnSync(process.execPath, ['-e', script, path.join(isolated, 'missing/slides.md')], { input: '{{ 1 + }}', encoding: 'utf8', timeout: 5000 })
  assert.match(missing.stdout, /slidev-expression\tunavailable\t/)
} finally { fs.rmSync(isolated, { recursive: true, force: true }) }
console.log(`PASS: real Vue compiler valid/invalid expressions, directives, TypeScript and subprocess protocol (${valid.length + 6} cases)`)
