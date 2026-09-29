const LOG_PREFIX = '[clipboard]';
/** Radix portals the open dialog/sheet with this role; the focus trap only lets selection live inside it. */
const FOCUS_SCOPE_SELECTOR = '[role="dialog"], [role="alertdialog"]';

/**
 * Copies text to the clipboard. The async Clipboard API exists only in secure contexts (HTTPS or localhost), so on
 * the plain-HTTP IP deployment it is `undefined` and we fall back to a hidden textarea + `execCommand('copy')`.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  if (canUseAsyncClipboard()) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (error) {
      console.warn(LOG_PREFIX, 'async write failed, falling back', errorName(error));
    }
  }
  return copyWithExecCommand(text);
}

function canUseAsyncClipboard(): boolean {
  return (
    typeof window !== 'undefined' && window.isSecureContext && !!navigator.clipboard?.writeText
  );
}

function copyWithExecCommand(text: string): boolean {
  if (typeof document === 'undefined') return false;
  const previousFocus =
    document.activeElement instanceof HTMLElement ? document.activeElement : null;
  const textarea = createOffscreenTextarea(text);
  focusScopeContainer(previousFocus).appendChild(textarea);
  try {
    textarea.focus({ preventScroll: true });
    textarea.select();
    textarea.setSelectionRange(0, text.length);
    return document.execCommand('copy');
  } catch (error) {
    console.warn(LOG_PREFIX, 'execCommand fallback failed', errorName(error));
    return false;
  } finally {
    textarea.remove();
    previousFocus?.focus({ preventScroll: true });
  }
}

function createOffscreenTextarea(text: string): HTMLTextAreaElement {
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.readOnly = true;
  textarea.setAttribute('aria-hidden', 'true');
  textarea.tabIndex = -1;
  // Off-screen but still selectable; 16px font avoids iOS zoom-on-focus.
  Object.assign(textarea.style, {
    position: 'fixed',
    top: '0',
    left: '-9999px',
    opacity: '0',
    fontSize: '16px',
  });
  return textarea;
}

/** Inside a modal, a textarea appended to <body> would have its focus stolen back by the focus trap. */
function focusScopeContainer(from: HTMLElement | null): HTMLElement {
  return from?.closest<HTMLElement>(FOCUS_SCOPE_SELECTOR) ?? document.body;
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : 'unknown';
}
