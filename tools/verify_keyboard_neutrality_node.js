const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const root = process.argv[2];
if (!root) throw new Error('usage: node verify_keyboard_neutrality_node.js <source-root>');
const adapterPath = `${root}/app/src/main/assets/chatgpt_bridge/site_adapter.js`;
const contentPath = `${root}/app/src/main/assets/chatgpt_bridge/content.js`;

const documentHandlers = new Map();
const windowHandlers = new Map();
const editorHandlers = new Map();
let execCalls = 0;
let clicks = 0;
let connects = 0;
let nativeListener = null;
const nativeMessages = [];
const timeoutQueue = [];

const sendControl = {
  isConnected: true, disabled: false,
  getAttribute: () => null,
  getBoundingClientRect: () => ({width: 20, height: 20}),
  contains: () => false,
  click: () => { clicks += 1; }
};
const form = {querySelector: () => sendControl};
const editor = {
  isConnected: true,
  tagName: 'TEXTAREA',
  value: 'abc',
  selectionStart: 3,
  selectionEnd: 3,
  childElementCount: 0,
  getBoundingClientRect: () => ({width: 200, height: 40}),
  matches: (selector) => selector === '#prompt-textarea' || selector.includes('textarea[name='),
  closest: (selector) => selector === 'form' ? form : null,
  contains: (node) => node === editor,
  querySelectorAll: () => ({length: 0}),
  focus: () => {},
  setRangeText: () => { execCalls += 1; },
  dispatchEvent: () => true,
  addEventListener: (type, fn) => editorHandlers.set(type, fn),
  removeEventListener: (type, fn) => { if (editorHandlers.get(type) === fn) editorHandlers.delete(type); }
};

const port = {
  onMessage: {addListener: (fn) => { nativeListener = fn; }},
  onDisconnect: {addListener: () => {}},
  postMessage: () => {}
};

const context = {
  console,
  Promise,
  InputEvent: function InputEvent(){},
  Event: function Event(){},
  MutationObserver: function MutationObserver(){ this.observe=()=>{}; this.disconnect=()=>{}; },
  window: {
    getComputedStyle: () => ({visibility: 'visible', display: 'block'}),
    addEventListener: (type, fn) => windowHandlers.set(type, fn)
  },
  document: {
    activeElement: editor,
    visibilityState: 'visible',
    addEventListener: (type, fn) => documentHandlers.set(type, fn),
    removeEventListener: (type, fn) => { if (documentHandlers.get(type) === fn) documentHandlers.delete(type); },
    querySelector: (selector) => selector.includes('prompt-textarea') ? editor : null,
    getSelection: () => ({anchorNode: editor, focusNode: editor, anchorOffset: 3, focusOffset: 3}),
    execCommand: () => { execCalls += 1; return true; }
  },
  browser: {runtime: {
    sendNativeMessage: (_app, message) => { nativeMessages.push(message); return Promise.resolve(null); },
    connectNative: () => { connects += 1; return port; }
  }},
  setTimeout: (fn) => { timeoutQueue.push(fn); return timeoutQueue.length; },
  clearTimeout: () => {},
};
vm.createContext(context);
vm.runInContext(fs.readFileSync(adapterPath, 'utf8'), context);
vm.runInContext(fs.readFileSync(contentPath, 'utf8'), context);
assert.equal(connects, 1, 'passive bridge still connects to Native once');
const passiveState = context.ChatGPTWebSiteAdapter.snapshot(false);
assert.equal(Object.prototype.hasOwnProperty.call(passiveState, 'sendReady'), false,
  'passive snapshot has no sendReady authority');
assert.equal(passiveState.attachmentActive, true,
  'attachment-only composer is represented by narrow passive safety metadata');

const makeKey = (shiftKey) => ({
  target: editor, key: 'Enter', shiftKey,
  ctrlKey: false, altKey: false, metaKey: false, repeat: false, isComposing: false,
  prevented: false, stopped: false,
  preventDefault(){ this.prevented = true; },
  stopImmediatePropagation(){ this.stopped = true; }
});

for (const shift of [false, true]) {
  const e = makeKey(shift);
  const handler = windowHandlers.get('keydown');
  if (handler) handler(e);
  assert.equal(e.prevented, false, `Enter shift=${shift} must remain browser-owned`);
  assert.equal(e.stopped, false, `Enter shift=${shift} propagation must remain browser-owned`);
}
assert.equal(execCalls, 0, 'extension must never mutate editor in response to Enter');
assert.equal(clicks, 0, 'extension must never click submit in response to Enter');
assert.equal(windowHandlers.has('keydown'), false, 'extension must not register semantic keydown handler');
assert.equal(windowHandlers.has('beforeinput'), false, 'extension must not register semantic beforeinput handler');

assert.equal(typeof nativeListener, 'function', 'passive native port listener remains installed');
nativeListener({type: 'PING'});
assert(nativeMessages.some((m) => m && m.type === 'PONG'), 'PING/PONG health path remains available');

console.log('KEYBOARD_NEUTRALITY_NODE_QA_OK');
