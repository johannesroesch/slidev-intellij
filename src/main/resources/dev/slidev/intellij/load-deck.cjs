'use strict'
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const { pathToFileURL } = require('node:url')
const encode = value => Buffer.from(String(value), 'utf8').toString('base64')
const decode = value => Buffer.from(value, 'base64').toString('utf8')
const output = (...values) => process.stdout.write(`slidev-deck\t${values.join('\t')}\n`)
function resolveParser(entry, name = '@slidev/parser/fs') {
  for (let dir = path.dirname(entry); ; dir = path.dirname(dir)) {
    try { return createRequire(path.join(dir, '__slidev_ide__.cjs')).resolve(name) } catch (_) {}
    const cli = path.join(dir, 'node_modules/@slidev/cli/bin/slidev.mjs')
    if (fs.existsSync(cli)) try { return createRequire(fs.realpathSync(cli)).resolve(name) } catch (_) {}
    if (path.dirname(dir) === dir) break
  }
  throw new Error(`Local ${name} not found. Install the Slidev project dependencies.`)
}
async function load(entry, overrides = new Map()) {
  entry = path.resolve(entry)
  const parser = await import(pathToFileURL(resolveParser(entry)).href)
  const sources = new Map(), userRoot = path.dirname(entry)
  let workspace = userRoot
  for (let dir = userRoot; ; dir = path.dirname(dir)) {
    if (fs.existsSync(path.join(dir, 'package.json'))) { workspace = dir; break }
    if (path.dirname(dir) === dir) break
  }
  try {
    const vite = await import(pathToFileURL(resolveParser(entry, 'vite')).href)
    workspace = await vite.searchForWorkspaceRoot(workspace)
  } catch (_) {}
  const issues = [], watched = new Set(), parsed = new Map()
  let leaves = 0, visits = 0
  const inside = file => [workspace, userRoot].some(root => { const rel = path.relative(root, file); return !rel || (!rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel)) })
  async function preflight(file, range, stack = []) {
    file = path.resolve(file); watched.add(file)
    if (++visits > 10000 || stack.length >= 32) throw new Error('Deck expansion limit exceeded')
    if (stack.includes(file)) { issues.push({ path: file, offset: 0, message: 'Cyclic src import' }); return }
    if (!inside(file)) { issues.push({ path: file, offset: 0, message: 'Import outside supported workspace roots' }); return }
    if (!parsed.has(file)) {
      if (sources.size >= 256) throw new Error('Deck exceeds 256 source files')
      if (!fs.existsSync(file)) { issues.push({ path: stack.at(-1) || entry, offset: 0, message: `Imported source missing: ${file}` }); return }
      const text = overrides.has(file) ? overrides.get(file) : await fs.promises.readFile(file, 'utf8')
      if (text.length > 1000000) throw new Error('Source exceeds 1,000,000 characters')
      sources.set(file, text)
      try { parsed.set(file, await parser.parse(text, file)) }
      catch (error) { issues.push({ path: file, offset: 0, message: `Cannot parse source: ${error.message || error}` }); return }
    }
    const md = parsed.get(file)
    for (const index of parser.parseRangeString(md.slides.length, range)) {
      const slide = md.slides[index - 1]
      if (!slide) { issues.push({ path: file, offset: 0, message: `Invalid source slide ${index}` }); continue }
      if (slide.frontmatter.disabled || slide.frontmatter.hide) continue
      if (slide.frontmatter.src) {
        if (typeof slide.frontmatter.src !== 'string') { issues.push({ path: file, offset: 0, message: 'src must be a string' }); continue }
        const [raw, selected] = slide.frontmatter.src.split('#')
        const child = path.resolve(raw.startsWith('/') ? userRoot : path.dirname(file), raw.startsWith('/') ? raw.slice(1) : raw)
        await preflight(child, selected, [...stack, file])
      } else if (++leaves > 10000) throw new Error('Deck exceeds 10,000 slides')
    }
  }
  await preflight(entry)
  for (const extension of ['ts', 'js', 'mts', 'mjs']) {
    const custom = path.join(userRoot, `setup/preparser.${extension}`)
    watched.add(custom)
    if (fs.existsSync(custom)) issues.push({ path: entry, offset: 0, message: 'Custom preparser detected; automatic deck mapping is unavailable.' })
  }
  if (issues.length) return { entry, sources, slides: [], issues, watched }
  const sourceRecord = {}
  for (const [file, text] of sources) { sourceRecord[file] = text; sourceRecord[file.replace(/\\/g, '/')] = text }
  // The record API also works with older parser releases that lack a reader callback.
  const options = typeof parser.isPathInsideRoots === 'function' ? { userRoot, roots: [userRoot], allowedRoots: [workspace, userRoot] } : userRoot
  const data = await parser.load(options, entry, sourceRecord)
  if (data.slides.length > 10000) throw new Error('Deck exceeds 10,000 slides')
  const offsets = file => {
    const text = sources.get(path.resolve(file))
    if (text === undefined) throw new Error(`Source map missing for ${file}`)
    const starts = [0]
    for (let i = 0; i < text.length; i++) if (text[i] === '\n') starts.push(i + 1)
    return line => starts[line] ?? text.length
  }
  const site = source => {
    const at = offsets(source.filepath)
    return { path: path.resolve(source.filepath), local: source.index + 1, start: at(source.start), end: at(source.end) }
  }
  const slides = data.slides.map((slide, index) => {
    const source = slide.source
    const at = offsets(source.filepath)
    return { number: index + 1, path: path.resolve(source.filepath), local: source.index + 1,
      start: at(source.start), end: at(source.end), content: at(source.contentStart), title: slide.title || source.title || 'Untitled slide',
      sites: (slide.importChain || []).map(site) }
  })
  for (const md of Object.values(data.markdownFiles)) {
    const at = offsets(md.filepath)
    for (const error of md.errors || []) issues.push({ path: path.resolve(md.filepath), offset: at(error.row || 0), message: error.message })
    for (const slide of md.slides) if (typeof slide.frontmatter.src === 'string') {
      const raw = slide.frontmatter.src.split('#')[0]
      watched.add(path.resolve(raw.startsWith('/') ? path.dirname(entry) : path.dirname(md.filepath), raw.startsWith('/') ? raw.slice(1) : raw))
    }
  }
  return { entry, sources, slides, issues, watched }
}
if (!module.parent) (async () => {
  try {
    const overrides = new Map()
    const lines = fs.readFileSync(0, 'utf8').split('\n')
    for (const line of lines) if (line) {
      const [file, text] = line.split('\t')
      overrides.set(path.resolve(decode(file)), decode(text))
    }
    const data = await load(process.argv[1], overrides)
    output('ready')
    for (const [file, text] of data.sources) output('source', encode(file), encode(text))
    for (const slide of data.slides) {
      const sites = slide.sites.map(site => [encode(site.path), site.local, site.start, site.end].join('\t')).join('\n')
      output('slide', encode(slide.path), slide.local, slide.start, slide.content, slide.end, encode(slide.title), encode(sites))
    }
    for (const issue of data.issues) output('issue', encode(issue.path), issue.offset, encode(issue.message))
    for (const file of data.watched) output('watch', encode(file))
  } catch (error) { output('unavailable', encode(error.message || error)) }
})()
module.exports = { load, resolveParser }
