// Runs with the presentation's Node executable. Never evaluates presentation expressions.
'use strict'
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const prefix = 'slidev-expression\t'
const encode = value => Buffer.from(String(value), 'utf8').toString('base64')
const unavailable = reason => process.stdout.write(`${prefix}unavailable\t${encode(reason)}\n`)

function loadCompiler(entry) {
  for (let directory = path.dirname(path.resolve(entry)); ; directory = path.dirname(directory)) {
    const local = createRequire(path.join(directory, '__slidev_ide__.cjs'))
    try { return local('@vue/compiler-dom') } catch (_) {}
    // Also works with pnpm's isolated transitive dependencies.
    const cli = path.join(directory, 'node_modules/@slidev/cli/bin/slidev.mjs')
    if (fs.existsSync(cli)) {
      try { return createRequire(fs.realpathSync(cli))('@vue/compiler-dom') } catch (_) {}
    }
    if (path.dirname(directory) === directory) break
  }
  return null
}

function check(source, compiler) {
  const names = [
    'X_INVALID_EXPRESSION', 'X_V_FOR_MALFORMED_EXPRESSION',
    'X_V_FOR_NO_EXPRESSION', 'X_V_IF_NO_EXPRESSION',
    'X_V_BIND_NO_EXPRESSION', 'X_V_ON_NO_EXPRESSION',
    'X_V_MODEL_NO_EXPRESSION', 'X_V_MODEL_MALFORMED_EXPRESSION',
    'X_MISSING_INTERPOLATION_END', 'X_V_HTML_NO_EXPRESSION', 'X_V_TEXT_NO_EXPRESSION',
  ]
  const codes = new Set(names.flatMap(name => [compiler.ErrorCodes?.[name], compiler.DOMErrorCodes?.[name]]).filter(value => typeof value === 'number'))
  const diagnostics = new Map()
  const onError = error => {
    if (!codes.has(error.code) || !error.loc) return
    const start = error.loc.start.offset
    const end = error.loc.end.offset
    if (!Number.isInteger(start) || !Number.isInteger(end)) return
    const message = String(error.message)
    diagnostics.set(`${start}:${end}:${message}`, { start, end, message })
  }
  try {
    compiler.compile(source, {
      mode: 'module', prefixIdentifiers: true, expressionPlugins: ['typescript'],
      onError, onWarn() {},
    })
  } catch (error) {
    // Recovery after an unrelated template error can fail during code generation.
    // Keep diagnostics collected by Vue; do not mislabel a compiler failure as a clean check.
    if (!diagnostics.size) throw error
  }
  return [...diagnostics.values()]
}

if (!module.parent) {
  try {
    const source = fs.readFileSync(0, 'utf8')
    const compiler = loadCompiler(process.argv[1])
    if (!compiler) unavailable('Local @vue/compiler-dom not found. Install the Slidev project dependencies.')
    else {
      const diagnostics = check(source, compiler)
      process.stdout.write(`${prefix}ready\n`)
      for (const diagnostic of diagnostics.slice(0, 200)) {
        process.stdout.write(`${prefix}error\t${diagnostic.start}\t${diagnostic.end}\t${encode(diagnostic.message)}\n`)
      }
    }
  } catch (error) { unavailable(error.message || error) }
}
module.exports = { loadCompiler, check }
