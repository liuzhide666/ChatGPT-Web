(() => {
  "use strict";

  const isVisibleElement = (el) => {
    if (!el || !el.isConnected) return false;
    const rect = el.getBoundingClientRect();
    const style = window.getComputedStyle(el);
    return rect.width > 0 && rect.height > 0
      && style.visibility !== "hidden" && style.display !== "none";
  };

  const isEditable = (el) => !!el && (
    el.matches?.("#prompt-textarea")
      || el.matches?.('textarea[name="prompt-textarea"]')
  );

  const findMountedComposer = () => {
    const editor = document.querySelector("#prompt-textarea")
      || document.querySelector('textarea[name="prompt-textarea"]');
    if (!isEditable(editor) || !isVisibleElement(editor)) return null;
    return editor;
  };

  const editorDirty = (editor, composing) => {
    if (!editor) return false;
    const value = editor.tagName === "TEXTAREA" ? editor.value : (editor.textContent || "");
    return !!composing || value.trim().length > 0;
  };

  const hasAttachment = (editor) => {
    if (!editor) return false;
    const root = editor.closest?.("form") || editor.parentElement?.parentElement || editor.parentElement;
    if (!root?.querySelector) return false;
    return !!root.querySelector([
      '[data-testid*="attachment"]',
      '[data-testid*="file-preview"]',
      '[data-testid*="upload-preview"]',
      '[data-testid*="file-thumbnail"]'
    ].join(','));
  };

  const snapshot = (composing) => {
    const editor = findMountedComposer();
    const active = document.activeElement;
    return {
      focused: !!editor && active === editor,
      dirty: !!editor && editorDirty(editor, composing),
      composing: !!composing,
      attachmentActive: hasAttachment(editor),
      mainComposer: !!editor
    };
  };

  globalThis.ChatGPTWebSiteAdapter = Object.freeze({snapshot});
})();
