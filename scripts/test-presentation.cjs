const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const listeners = new Map();
const reports = [];
const document = {
  fullscreenElement: null,
  addEventListener(type, fn) {
    if (!listeners.has(type)) listeners.set(type, new Set());
    listeners.get(type).add(fn);
  },
  removeEventListener(type, fn) { listeners.get(type)?.delete(fn); }
};
const window = {};
const context = vm.createContext({ document, window, reports });
const script = fs.readFileSync(path.join(__dirname, '../src/main/resources/dev/slidev/intellij/presentation.js'), 'utf8')
  .replace('/*REPORT*/', 'reports.push(message);');
const emit = (type, event={}) => { for (const fn of listeners.get(type) ?? []) fn(event); };
vm.runInContext(script, context);
vm.runInContext(script, context);
assert.equal(listeners.get('fullscreenchange').size, 1, 'Repeated page initialization has one handler');
document.fullscreenElement = {};
emit('fullscreenchange');
assert.equal(reports.at(-1), 'fullscreen:1', 'Native fullscreen entry requests presentation view');
const count = reports.length;
emit('keydown', { key: 'ArrowRight' });
assert.equal(reports.length, count, 'Slide navigation is not intercepted');
emit('keydown', { key: 'Escape' });
assert.equal(reports.at(-1), 'escape', 'Escape requests return to editor');
document.fullscreenElement = null;
emit('fullscreenchange');
assert.equal(reports.at(-1), 'fullscreen:0', 'Native fullscreen exit requests return to editor');
window.__slidevIdePresentation.dispose();
assert.equal(listeners.get('fullscreenchange').size, 0);
assert.equal(listeners.get('keydown').size, 0);
console.log('PASS: fullscreen entry/exit, Escape, repeat initialization and bridge cleanup (DOM fixture)');
