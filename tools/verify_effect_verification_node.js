const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

let nativeHandler = null;
const nativePortMessages = [];
const nativeMessages = [];
const timeouts = [];
let now = 1000;
let mode = 'none';
let brCount = 0;
let selectionOffset = 0;
const editorListeners = new Map();
const mutationObservers = new Set();

const sendButton = {
  isConnected: true, disabled: false,
  getAttribute: (name) => name === 'aria-disabled' ? 'false' : null,
  getBoundingClientRect: () => ({width:20,height:20}),
  click: () => {}
};
const form = { querySelector: () => sendButton };
const editor = {
  isConnected: true,
  tagName: 'DIV',
  isContentEditable: true,
  textContent: 'fixture',
  childElementCount: 0,
  getBoundingClientRect: () => ({width:200,height:40}),
  matches: (s) => s === '#prompt-textarea',
  closest: (s) => s === 'form' ? form : null,
  querySelectorAll: (s) => ({length: s.includes('br') ? brCount : 0}),
  addEventListener: (type, fn) => editorListeners.set(type, fn),
  removeEventListener: (type, fn) => { if (editorListeners.get(type) === fn) editorListeners.delete(type); },
  focus: () => {}
};

function fireInput(inputType='insertLineBreak') {
  const fn = editorListeners.get('input');
  if (fn) fn({target: editor, inputType});
}
function fireMutation() {
  for (const obs of [...mutationObservers]) obs.cb([{type:'childList'}]);
}
class MutationObserverMock {
  constructor(cb){ this.cb=cb; }
  observe(){ mutationObservers.add(this); }
  disconnect(){ mutationObservers.delete(this); }
}

const port = {
  onMessage: { addListener: (fn) => { nativeHandler = fn; } },
  onDisconnect: { addListener: () => {} },
  postMessage: (m) => nativePortMessages.push(m)
};
const handlers = new Map();
const context = {
  console, Promise,
  performance: {now:()=>now},
  MutationObserver: MutationObserverMock,
  window: {
    getComputedStyle: () => ({visibility:'visible',display:'block'}),
    addEventListener: (type,fn) => handlers.set(type,fn),
  },
  document: {
    activeElement: editor,
    visibilityState: 'visible',
    addEventListener: (type,fn) => handlers.set(type,fn),
    querySelector: (s) => s.includes('#prompt-textarea') ? editor : null,
    getSelection: () => ({anchorNode:editor,focusNode:editor,anchorOffset:selectionOffset,focusOffset:selectionOffset}),
    execCommand: (command) => {
      if (command !== 'insertLineBreak') return false;
      if (mode === 'applied') {
        brCount = 1; selectionOffset = 1; editor.childElementCount = 1;
        fireInput(); fireMutation();
      } else if (mode === 'reverted') {
        brCount = 1; selectionOffset = 1; editor.childElementCount = 1;
        fireInput(); fireMutation();
        brCount = 0; selectionOffset = 0; editor.childElementCount = 0;
        fireMutation();
      }
      return true;
    }
  },
  browser: {runtime:{
    sendNativeMessage: (_app,m) => {nativeMessages.push(m); return Promise.resolve(null);},
    connectNative: () => port,
  }},
  setTimeout: (fn,delay=0) => {timeouts.push({fn,due:now+delay}); return timeouts.length;},
  clearTimeout:()=>{},
  setInterval:()=>1,
};
vm.createContext(context);
for (const p of process.argv.slice(2)) vm.runInContext(fs.readFileSync(p,'utf8'), context);
assert(nativeHandler, 'native command handler installed');
assert.equal(typeof context.ChatGPTWebSiteAdapter?.observeEditorEffect, 'function', 'effect observer must exist');

function flush(){
  let ran=true;
  while(ran){ ran=false; for(let i=0;i<timeouts.length;i++){ if(timeouts[i].due<=now){ const x=timeouts.splice(i,1)[0]; x.fn(); ran=true; break; } } }
}
function run(id, nextMode){
  mode=nextMode; brCount=0; selectionOffset=0; editor.childElementCount=0;
  nativePortMessages.length=0; nativeMessages.length=0;
  nativeHandler({type:'COMMAND',commandId:id,command:'INSERT_LINE_BREAK'});
  assert(!nativePortMessages.some(m => m.type==='COMMAND_ACK' && m.result==='APPLIED'),
    'APPLIED must never be emitted synchronously from executor return value');
  flush();
  return nativePortMessages.find(m => m.type==='COMMAND_ACK');
}

let ack=run(501,'none');
assert(ack && ack.result !== 'APPLIED', 'executor true without editor effect must not apply');
ack=run(502,'reverted');
assert(ack && ack.result !== 'APPLIED', 'immediate framework revert must not apply');
ack=run(503,'applied');
assert.deepEqual(ack,{type:'COMMAND_ACK',commandId:503,command:'INSERT_LINE_BREAK',result:'APPLIED'},
  'persistent one logical line-break effect should apply');
const effects=nativeMessages.filter(m => m.type==='EDITOR_EFFECT');
assert(effects.some(m => m.stage==='EXECUTOR_ACCEPTED'), 'executor accepted stage emitted');
assert(effects.some(m => m.stage==='EDITOR_EFFECT_OBSERVED' && m.editorChanged===true && m.submitObserved===false),
  'effect observation metadata emitted');
console.log('EFFECT_VERIFICATION_NODE_QA_OK');
