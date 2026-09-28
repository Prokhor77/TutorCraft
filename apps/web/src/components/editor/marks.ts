import { isSafeHref } from '@/lib/blockdoc/richtext';

export type ToolbarMark = 'bold' | 'italic' | 'underline' | 'strike' | 'code';

const EXEC_COMMANDS: Partial<Record<ToolbarMark, string>> = {
  bold: 'bold',
  italic: 'italic',
  underline: 'underline',
  strike: 'strikeThrough',
};

function activeEditable(): HTMLElement | null {
  const node = window.getSelection()?.anchorNode ?? null;
  const element = node instanceof HTMLElement ? node : (node?.parentElement ?? null);
  return element?.closest<HTMLElement>('[contenteditable="true"]') ?? null;
}

function notifyInput(target: HTMLElement | null): void {
  target?.dispatchEvent(new Event('input', { bubbles: true }));
}

/** Applies an inline mark to the current selection inside a RichTextEditable. */
export function applyMark(mark: ToolbarMark): void {
  const command = EXEC_COMMANDS[mark];
  if (command) {
    document.execCommand(command);
    return;
  }
  wrapSelection('code');
}

function wrapSelection(tagName: string): void {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return;
  const editable = activeEditable();
  if (!editable) return;
  const range = selection.getRangeAt(0);
  const wrapper = document.createElement(tagName);
  wrapper.appendChild(range.extractContents());
  range.insertNode(wrapper);
  selection.removeAllRanges();
  notifyInput(editable);
}

export type SavedSelection = { range: Range; editable: HTMLElement } | null;

export function saveSelection(): SavedSelection {
  const selection = window.getSelection();
  const editable = activeEditable();
  if (!selection || selection.rangeCount === 0 || !editable) return null;
  return { range: selection.getRangeAt(0).cloneRange(), editable };
}

/** Restores a saved selection and turns it into a link. Returns false for unsafe URLs. */
export function applyLink(saved: SavedSelection, url: string): boolean {
  if (!saved || !isSafeHref(url)) return false;
  const selection = window.getSelection();
  saved.editable.focus();
  selection?.removeAllRanges();
  selection?.addRange(saved.range);
  document.execCommand('createLink', false, url);
  notifyInput(saved.editable);
  return true;
}
