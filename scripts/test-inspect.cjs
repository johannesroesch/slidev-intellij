'use strict'
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { createRequire } = require('node:module')
const { pathToFileURL } = require('node:url')
;(async () => {
  const project = path.resolve(process.argv[2])
  const fixture = createRequire(path.join(project, 'package.json'))
  const { createServer } = await import(pathToFileURL(fixture.resolve('vite')).href)
  const { default: Inspect } = await import(pathToFileURL(fixture.resolve('vite-plugin-inspect')).href)
  const modern = process.argv[3] === 'devtools'
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'slidev-inspect-'))
  fs.symlinkSync(path.join(project, 'node_modules'), path.join(root, 'node_modules'), process.platform === 'win32' ? 'junction' : 'dir')
  fs.writeFileSync(path.join(root, 'index.html'), '<html><body>Presentation fixture</body></html>')
  fs.writeFileSync(path.join(root, 'main.js'), 'export const slide = 1')
  let server
  try {
    server = await createServer({ root, configFile: false, clearScreen: false, logLevel: 'silent', devtools: modern, plugins: [Inspect({ dev: true, build: true, outputDir: path.join(root, ".vite-inspect") })], server: { host: '127.0.0.1', port: 0 }, optimizeDeps: { noDiscovery: true } })
    await server.listen()
    const origin = `http://127.0.0.1:${server.httpServer.address().port}`
    assert.equal((await fetch(origin + '/main.js')).status, 200)
    const route = await fetch(origin + '/__inspect/', { redirect: 'manual' })
    if (modern) assert.equal(route.status, 302, 'Inspect v12 redirects to its client')
    const page = await fetch(origin + '/__inspect/')
    assert.equal(page.status, 200)
    const html = await page.text()
    assert(/inspect/i.test(html) && /<script/.test(html), 'Inspector HTML, not presentation history fallback')
    if (modern) {
      const metadata = await fetch(origin + '/__devtools/__connection.json')
      assert.match(metadata.headers.get('content-type') || '', /json/)
      assert([200, 401, 403].includes(metadata.status), 'DevTools metadata or authentication gate')
    }
    console.log(`PASS: actual ${modern ? 'Inspect v12 / Vite 8 with DevTools' : 'Inspect v11 / Vite 7'} UI route and module transform`)
  } finally { if (server) await server.close(); fs.rmSync(root, { recursive: true, force: true }) }
})().catch(error => { console.error(error); process.exitCode = 1 })
