// Standalone bridge contract tests. Optional argument: real Slidev useEmbeddedCtrl.ts.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../src/main/resources/dev/slidev/intellij/navigation.js'), 'utf8')
  .replace('/*REPORT*/', 'reports.push(message);');
let clock = 0;
const timers = new Map();
const reports = [], commands = [], calls = [], listeners = [];
let nextTimer = 0;
const nav = { currentSlideNo: 2, clicks: 4, isEmbedded: { value: false },
  go(no, clicks) { calls.push([no, clicks]);this.currentSlideNo = no;this.clicks = clicks; } };
const window = {
  location: { origin: 'http://localhost:1234' },
  __slidev__: { nav },
  addEventListener(type, fn) { if (type === 'message') listeners.push(fn); },
  postMessage(data, origin) {
    assert.equal(origin, window.location.origin);
    commands.push(data);
    for (const fn of listeners) fn({ data });
  }
};
const context = vm.createContext({ window, reports, useNav: () => nav, isDark: { value: false },
  throttledWatch() { throw Error('Embedded mode is not required'); },
  Date: { now: () => clock },
  setInterval(fn) { const id=++nextTimer;timers.set(id, fn);return id; },
  clearInterval(id) { timers.delete(id); }
});
if (process.argv[2]) {
  const source = fs.readFileSync(process.argv[2], 'utf8').replace(/^import .*$/gm, '')
    .replace('export function useEmbeddedControl()', 'function useEmbeddedControl()')
    .replace('nav[data.operation as keyof typeof nav]', 'nav[data.operation]')
    .replace(/\bas any\b/g, '');
  vm.runInContext(source + '\nuseEmbeddedControl();', context);
} else {
  listeners.push(({ data }) => {
    if (data.target === 'slidev' && data.type === 'navigate') nav.go(+data.no, +data.clicks || 0);
  });
}
const tick = () => { for (const fn of timers.values()) fn(); };
vm.runInContext(script, context);
tick();
assert.deepEqual(reports, ['ready', 'page:2']);
vm.runInContext(script, context);
assert.equal(timers.size, 1, 'Repeated load does not duplicate the observer');
window.__slidevIdeNavigation.navigate(2);
assert.equal(calls.length, 0, 'Same slide does not reset clicks');
assert.equal(nav.clicks, 4);
window.__slidevIdeNavigation.navigate(5);
assert.equal(commands.length, 1);
assert.equal(commands[0].target, 'slidev');
assert.equal(commands[0].type, 'navigate');
assert.deepEqual(calls[0], [5, 0]);
tick();
assert.equal(reports.at(-1), 'page:5');
const count=reports.length;
nav.clicks=3;tick();
assert.equal(reports.length, count, 'Click-only changes do not move editor cursor');
nav.currentSlideNo=7;tick();
assert.equal(reports.at(-1), 'page:7', 'Preview navigation reports the new slide');
window.__slidevIdeNavigation.navigate(-1);
window.__slidevIdeNavigation.navigate(1.2);
assert.equal(commands.length, 1);
window.__slidev__ = null;
clock=11000;tick();tick();
assert.equal(reports.filter(message => message.startsWith('error:')).length, 1);
window.__slidev__ = { nav };nav.currentSlideNo=8;tick();
assert.equal(reports.at(-1), 'page:8', 'Observer survives replacement of HMR navigation context');
window.__slidevIdeNavigation.dispose();
assert.equal(timers.size, 0);
console.log('PASS: navigation bridge API commands, reverse reports, click preservation, HMR and disposal' +
  (process.argv[2] ? ' against supplied Slidev handler' : ' (contract fixture)'));
