'use strict'
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const prefix = 'slidev-expression\t'
const encode = value => Buffer.from(String(value), 'utf8').toString('base64')
function compilerFor(entry) {
  for (let dir = path.dirname(path.resolve(entry)); ; dir = path.dirname(dir)) {
    const local = createRequire(path.join(dir, '__slidev_vue_diagnostic__.cjs'))
    try { return local('vue/compiler-sfc') } catch (_) {}
    const cli = path.join(dir, 'node_modules/@slidev/cli/bin/slidev.mjs')
    if (fs.existsSync(cli)) try { return createRequire(fs.realpathSync(cli))('vue/compiler-sfc') } catch (_) {}
    if (path.dirname(dir) === dir) break
  }
  return null
}
function check(source, compiler) {
  const result = [], add = (start, end, message) => {
    if (Number.isInteger(start) && Number.isInteger(end) && start >= 0 && end >= start && end <= source.length)
      result.push({ start, end, message: String(message).split('\n')[0] })
  }
  const parsed = compiler.parse(source, { filename: 'slidev-diagnostic.vue' })
  for (const error of parsed.errors) if (error.loc) add(error.loc.start.offset, error.loc.end.offset, error.message)
  if (!parsed.errors.length) for (const name of ['script', 'scriptSetup']) {
    const block = parsed.descriptor[name]
    if (!block || block.src || (block.lang && !['js', 'jsx', 'ts', 'tsx'].includes(block.lang))) continue
    try {
      compiler.compileScript({ ...parsed.descriptor, [name === 'script' ? 'scriptSetup' : 'script']: null }, { id: 'slidev-diagnostic' })
    } catch (error) {
      // Babel syntax offsets are relative to this block. Macro/type-resolution errors
      // without a parser offset are deliberately not assigned a guessed source range.
      if (Number.isInteger(error.pos)) {
        const start = block.loc.start.offset + error.pos
        add(start, Math.min(source.length, start + 1), error.message)
      }
    }
  }
  return result
}
if (!module.parent) {
  try {
    const source = fs.readFileSync(0, 'utf8'), compiler = compilerFor(process.argv[1])
    if (!compiler) throw new Error('Local vue/compiler-sfc unavailable')
    const errors = check(source, compiler)
    process.stdout.write(`${prefix}ready\n`)
    for (const error of errors.slice(0, 200)) process.stdout.write(`${prefix}error\t${error.start}\t${error.end}\t${encode(error.message)}\n`)
  } catch (error) { process.stdout.write(`${prefix}unavailable\t${encode(error.message || error)}\n`) }
}
module.exports = { compilerFor, check }
