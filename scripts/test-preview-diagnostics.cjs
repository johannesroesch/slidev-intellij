'use strict'
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { createRequire } = require('node:module')
const { pathToFileURL } = require('node:url')
;(async () => {
  const requireFixture = createRequire(path.join(path.resolve(process.argv[2]), 'package.json'))
  const { createServer } = await import(pathToFileURL(requireFixture.resolve('vite')).href)
  const { default: vue } = await import(pathToFileURL(requireFixture.resolve('@vitejs/plugin-vue')).href)
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'slidev-preview-log-'))
  const target = path.join(root, 'slides.md__slidev_2.md')
  fs.symlinkSync(path.join(path.resolve(process.argv[2]), 'node_modules'), path.join(root, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir')
  const messages = []
  const logger = { info() {}, warn() {}, warnOnce() {}, clearScreen() {}, hasWarned: false, hasErrorLogged() { return false }, error(message) { messages.push(message) } }
  let server
  try {
    fs.writeFileSync(target, '<template>\n<h1>Slide</h1>\n{{ 1 + }}\n</template>')
    server = await createServer({ root, configFile: false, customLogger: logger, plugins: [vue({ include: /\.md$/ })], server: { port: 0, host: '127.0.0.1' }, optimizeDeps: { noDiscovery: true } })
    await server.listen()
    const url = `http://127.0.0.1:${server.httpServer.address().port}/slides.md__slidev_2.md?import`
    assert.equal((await fetch(url)).status, 500)
    assert(messages.some(message => /Internal server error:.*Error parsing JavaScript expression/.test(message)))
    assert(messages.some(message => /File: .*slides\.md__slidev_2\.md.*:3:4/.test(message)), messages.join('\n'))
    // Verify that the same running server recovers after a corrected module.
    server.watcher.add(target)
    await new Promise(resolve => setTimeout(resolve, 200))
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('File watcher did not report the corrected source')), 5000)
      server.watcher.once('change', () => { clearTimeout(timer); resolve() })
      fs.writeFileSync(target, '<template>\n<h1>Slide</h1>\n{{ 1 + 2 }}\n</template>')
    })
    const corrected = await fetch(url + '&t=' + Date.now())
    assert.equal(corrected.status, 200, messages.join('\n'))
    if (process.argv[3]) fs.writeFileSync(process.argv[3], messages.join('\n'))
    console.log('PASS: real Vite/Vue dev error log, virtual source coordinates and corrected-module recovery')
  } finally { if (server) await server.close(); fs.rmSync(root, { recursive: true, force: true }) }
})().catch(error => { console.error(error); process.exitCode = 1 })
