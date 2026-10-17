const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const handlers = new Map();
let nativeMessageHandler = null;
let nativeDisconnectHandler = null;
let connectNativeCalls = 0;
let clicks = 0;
let lineBreaks = 0;
let execLineBreakSucceeds = true;
let execInsertTextSucceeds = true;
let sendControlPresent = true;
let simulateNestedLineBreakEvents = true;
let now = 1000;
const nativeMessages = [];
const timeoutQueue = [];
const editorListeners = new Map();

const sendButton = {
  isConnected: true,
  disabled: false,
  getAttribute: (name) => name === 'aria-disabled' ? 'false' : null,
  getBoundingClientRect: () => ({width: 20, height: 20}),
  click: () => { clicks++; }
};
const form = { querySelector: () => sendControlPresent ? sendButton : null };
const editor = {
  isConnected: true,
  tagName: 'DIV',
  isContentEditable: true,
  textContent: 'hello',
  getBoundingClientRect: () => ({width: 200, height: 40}),
  matches: (selector) => selector === '#prompt-textarea',
  closest: (selector) => selector === 'form' ? form : null,
  querySelectorAll: (selector) => ({length: selector.includes('br') ? lineBreaks : 0}),
  contains: (node) => node === editor,
  addEventListener: (type, fn) => editorListeners.set(type, fn),
  removeEventListener: (type, fn) => { if (editorListeners.get(type) === fn) editorListeners.delete(type); },
};
const other = {
  isConnected: true,
  tagName: 'TEXTAREA',
  value: 'search',
  getBoundingClientRect: () => ({width: 200, height: 40}),
  matches: () => false,
  closest: () => form,
};

const nativePortMessages = [];
const port = {
  onMessage: { addListener: (fn) => { nativeMessageHandler = fn; } },
  onDisconnect: { addListener: (fn) => { nativeDisconnectHandler = fn; } },
  postMessage: (message) => { nativePortMessages.push(message); },
};
const context = {
  console,
  Promise,
  performance: { now: () => now },
  window: {
    getComputedStyle: () => ({visibility:'visible', display:'block'}),
    addEventListener: (type, fn) => handlers.set(type, fn),
  },
  document: {
    activeElement: editor,
    visibilityState: 'visible',
    addEventListener: (type, fn) => handlers.set(type, fn),
    removeEventListener: (type, fn) => { if (handlers.get(type) === fn) handlers.delete(type); },
    querySelector: (selector) => selector.includes('#prompt-textarea') || selector.includes('textarea[name=') ? editor : null,
    getSelection: () => ({anchorNode: editor, focusNode: editor, anchorOffset: lineBreaks, focusOffset: lineBreaks}),
    execCommand: (command, _ui, value) => {
      if (command === 'insertLineBreak') {
        if (!execLineBreakSucceeds) return false;
        if (simulateNestedLineBreakEvents && handlers.get('beforeinput')) {
          const nested = event({target:editor,inputType:'insertLineBreak'});
          handlers.get('beforeinput')(nested);
          assert.equal(nested.prevented,false,'execCommand nested beforeinput is allowed by reentrancy guard');
          if (handlers.get('input')) handlers.get('input')();
        }
        lineBreaks++;
        const editorInput = editorListeners.get('input');
        if (editorInput) editorInput({target: editor, inputType: 'insertLineBreak'});
        return true;
      }
      if (command === 'insertText' && value === '\n' && execInsertTextSucceeds) {
        lineBreaks++;
        const editorInput = editorListeners.get('input');
        if (editorInput) editorInput({target: editor, inputType: 'insertLineBreak'});
        return true;
      }
      return false;
    },
  },
  browser: { runtime: {
    sendNativeMessage: (_app, message) => { nativeMessages.push(message); return Promise.resolve(null); },
    connectNative: () => { connectNativeCalls++; return port; },
  }},
  setTimeout: (fn, delay=0) => { timeoutQueue.push({fn, due: now + delay}); return timeoutQueue.length; },
  setInterval: () => 1,
  clearTimeout: () => {},
};
vm.createContext(context);
for (const scriptPath of process.argv.slice(2)) {
  vm.runInContext(fs.readFileSync(scriptPath, 'utf8'), context);
}
assert.equal(connectNativeCalls,1,'content bridge opens one native port on startup');

function flushTimeouts(){
  let ran;
  do {
    ran = false;
    for (let i = 0; i < timeoutQueue.length; i++) {
      if (timeoutQueue[i].due <= now) {
        const item = timeoutQueue.splice(i, 1)[0];
        item.fn();
        ran = true;
        break;
      }
    }
  } while (ran);
}
function lastComposerState(){
  const states = nativeMessages.filter((m) => m && m.type === 'COMPOSER_STATE');
  return states[states.length - 1];
}

function event(extra={}) {
  return Object.assign({
    target: editor, key: '', shiftKey:false, ctrlKey:false, altKey:false, metaKey:false,
    repeat:false, isComposing:false, inputType:'', prevented:false,
    preventDefault(){ this.prevented=true; },
    propagationStopped:false, stopImmediatePropagation(){ this.propagationStopped=true; }
  }, extra);
}

clicks=0;
let e=event({key:'Enter'}); handlers.get('keydown')(e);
assert.equal(clicks,1,'plain hardware Enter submits');
assert.equal(e.prevented,true,'plain hardware Enter prevents newline after validated submit');
assert.equal(e.propagationStopped,true,'plain hardware Enter is consumed so page handlers cannot submit twice');
flushTimeouts();

clicks=0; lineBreaks=0; now=2000;
e=event({key:'Enter',shiftKey:true}); handlers.get('keydown')(e);
assert.equal(clicks,0,'Shift+Enter never submits');
assert.equal(e.prevented,true,'Shift+Enter is owned before ChatGPT submit handlers');
assert.equal(e.propagationStopped,true,'Shift+Enter cannot fall through to page submit handlers');
assert.equal(lineBreaks,1,'Shift+Enter inserts exactly one hard line break');
flushTimeouts();

// A trailing browser beforeinput may arrive late on Android/Gecko. It must be suppressed
// by one-shot state, not by a short wall-clock guess.
now += 450;
let trailing=event({inputType:'insertLineBreak'}); handlers.get('beforeinput')(trailing);
assert.equal(trailing.prevented,true,'late trailing Shift+Enter beforeinput is suppressed');
assert.equal(clicks,0,'late trailing Shift+Enter beforeinput cannot submit');
assert.equal(lineBreaks,1,'late trailing Shift+Enter beforeinput cannot create a second break');

// If no trailing beforeinput arrives, a new physical Enter keydown must supersede the stale
// Shift guard so the next genuine Enter can still submit normally.
clicks=0; lineBreaks=0; now=2600;
e=event({key:'Enter',shiftKey:true}); handlers.get('keydown')(e);
assert.equal(lineBreaks,1,'second Shift+Enter inserts one break');
clicks=0;
e=event({key:'Enter'}); handlers.get('keydown')(e);
assert.equal(clicks,1,'new plain Enter clears stale Shift trailing guard and submits');
flushTimeouts();

// If insertLineBreak is unavailable, insertText(newline) is the fallback.
clicks=0; lineBreaks=0; execLineBreakSucceeds=false; execInsertTextSucceeds=true; now=3000;
e=event({key:'Enter',shiftKey:true}); handlers.get('keydown')(e);
assert.equal(clicks,0,'fallback hard-break path never submits');
assert.equal(lineBreaks,1,'insertText newline fallback creates one break');
assert.equal(e.prevented,true,'fallback hard-break path remains consumed');

// If both edit commands fail, Shift+Enter is still consumed instead of becoming Send.
clicks=0; lineBreaks=0; execInsertTextSucceeds=false; now=4000;
e=event({key:'Enter',shiftKey:true}); handlers.get('keydown')(e);
assert.equal(clicks,0,'failed hard-break insertion still never submits');
assert.equal(lineBreaks,0,'failed hard-break insertion reports failure without mutation');
assert.equal(e.prevented,true,'failed hard-break insertion remains consumed');
assert.equal(e.propagationStopped,true,'failed hard-break insertion cannot fall through');
execLineBreakSucceeds=true; execInsertTextSucceeds=true;

// Shift+Enter must still create a line break if React has not mounted the send button yet.
clicks=0; lineBreaks=0; sendControlPresent=false; now=4500;
e=event({key:'Enter',shiftKey:true}); handlers.get('keydown')(e);
assert.equal(lineBreaks,1,'Shift+Enter is editor-scoped, not send-button-mount-scoped');
assert.equal(clicks,0,'missing send control never turns Shift+Enter into submit');
assert.equal(e.prevented,true,'main editor Shift+Enter remains owned without send button');

// Plain Enter still requires a confirmed send control.
clicks=0;
e=event({key:'Enter'}); handlers.get('keydown')(e);
assert.equal(clicks,0,'plain Enter does not submit without a send control');
assert.equal(e.prevented,false,'plain Enter falls back to page semantics if send control is absent');
sendControlPresent=true;

clicks=0; now=5000;
let b=event({inputType:'insertParagraph'}); handlers.get('beforeinput')(b);
assert.equal(clicks,0,'DOM beforeinput is never a submit channel in native-first mode');
assert.equal(b.prevented,false,'unsuppressed DOM beforeinput remains browser-owned');
assert.equal(b.propagationStopped,false,'native-first DOM beforeinput does not consume unrelated browser editing');

clicks=0;
handlers.get('compositionstart')();
b=event({inputType:'insertParagraph',isComposing:true}); handlers.get('beforeinput')(b);
assert.equal(clicks,0,'composition Enter never submits');
assert.equal(b.prevented,false,'composition Enter remains IME-owned');
handlers.get('compositionend')();

clicks=0;
context.document.activeElement=other;
e=event({target:other,key:'Enter'}); handlers.get('keydown')(e);
assert.equal(clicks,0,'non-main textarea is untouched');
assert.equal(e.prevented,false,'non-main textarea keeps native Enter');

clicks=0;
context.document.activeElement=editor;
assert(nativeMessageHandler,'native port listener installed');
nativeMessageHandler({type:'COMMAND',commandId:101,command:'SUBMIT_COMPOSER'});
assert.equal(clicks,1,'trusted native semantic command submits current main composer');
assert.deepEqual(nativePortMessages.pop(),{type:'COMMAND_ACK',commandId:101,command:'SUBMIT_COMPOSER',result:'APPLIED'},'submit returns correlated ACK only after apply');

lineBreaks=0;
nativeMessageHandler({type:'COMMAND',commandId:102,command:'INSERT_LINE_BREAK'});
assert.equal(lineBreaks,1,'native semantic line-break command mutates the main composer without a keyboard DOM event');
assert(!nativePortMessages.some((m) => m.commandId === 102 && m.result === 'APPLIED'),
  'line-break does not ACK APPLIED before editor effect observation');
flushTimeouts();
assert.deepEqual(nativePortMessages.pop(),{type:'COMMAND_ACK',commandId:102,command:'INSERT_LINE_BREAK',result:'APPLIED'},'line-break returns correlated ACK only after effect-verified apply');
nativeMessageHandler({type:'PING'});
assert(nativeMessages.some((m) => m && m.type === 'PONG'),'native PING produces metadata-only PONG');

const stateCountBeforeSync = nativeMessages.filter((m) => m && m.type === 'COMPOSER_STATE').length;
nativeMessageHandler({type:'SYNC_COMPOSER_STATE', generation:73});
const syncStates = nativeMessages.filter((m) => m && m.type === 'COMPOSER_STATE');
assert.equal(syncStates.length, stateCountBeforeSync + 1, 'SYNC_COMPOSER_STATE forces a fresh composer snapshot');
assert.equal(syncStates[syncStates.length - 1].inputGeneration, 73, 'composer sync echoes the native InputConnection generation');


// Health metadata must preserve unsent editor/send state even when focus leaves the composer.
context.document.activeElement=other;
handlers.get('focusout')();
flushTimeouts();
let state=lastComposerState();
assert.equal(state.mainComposer,true,'mounted main composer remains known while blurred');
assert.equal(state.focused,false,'blurred composer is reported unfocused');
assert.equal(state.dirty,true,'unsent text remains dirty while blurred');
assert.equal(state.sendReady,true,'send-ready state survives blur for recovery safety');

// React may enable/mount the send control after the input event; deferred refresh must observe it.
context.document.activeElement=editor;
sendButton.disabled=true;
handlers.get('input')();
state=lastComposerState();
assert.equal(state.sendReady,false,'capture-phase input can observe pre-React disabled send state');
sendButton.disabled=false;
flushTimeouts();
state=lastComposerState();
assert.equal(state.sendReady,true,'deferred composer refresh observes post-React send readiness');


assert.equal(typeof nativeDisconnectHandler,'function','native port disconnect handler is registered');
nativeDisconnectHandler();
assert.equal(connectNativeCalls,1,'disconnect schedules reconnect instead of reconnecting synchronously');
now += 1000;
flushTimeouts();
assert.equal(connectNativeCalls,2,'content bridge reconnects native port after bounded delay');

console.log('INPUT_ROUTER_NODE_QA_OK');
