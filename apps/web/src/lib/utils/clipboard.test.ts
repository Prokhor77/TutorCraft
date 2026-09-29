import { afterEach, describe, expect, it, vi } from 'vitest';
import { copyToClipboard } from './clipboard';

function setSecureContext(value: boolean) {
  Object.defineProperty(window, 'isSecureContext', { value, configurable: true });
}

function stubExecCommand(result: boolean) {
  const exec = vi.fn(() => result);
  Object.defineProperty(document, 'execCommand', { value: exec, configurable: true });
  return exec;
}

describe('copyToClipboard', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });

  it('uses the async Clipboard API in a secure context', async () => {
    setSecureContext(true);
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const exec = stubExecCommand(true);

    await expect(copyToClipboard('abc')).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith('abc');
    expect(exec).not.toHaveBeenCalled();
  });

  it('falls back to execCommand over plain HTTP and cleans up', async () => {
    setSecureContext(false);
    const exec = stubExecCommand(true);

    await expect(copyToClipboard('http://1.2.3.4/x')).resolves.toBe(true);
    expect(exec).toHaveBeenCalledWith('copy');
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('places the helper textarea inside the focused dialog and restores focus', async () => {
    setSecureContext(false);
    const dialog = document.createElement('div');
    dialog.setAttribute('role', 'dialog');
    const button = document.createElement('button');
    dialog.appendChild(button);
    document.body.appendChild(dialog);
    button.focus();
    let parentDuringCopy: Element | null = null;
    stubExecCommand(true).mockImplementation(() => {
      parentDuringCopy = document.querySelector('textarea')?.parentElement ?? null;
      return true;
    });

    await copyToClipboard('x');
    expect(parentDuringCopy).toBe(dialog);
    expect(document.activeElement).toBe(button);
  });

  it('falls back when the async API rejects, and reports failure if both fail', async () => {
    setSecureContext(true);
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const writeText = vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError'));
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    stubExecCommand(false);

    await expect(copyToClipboard('x')).resolves.toBe(false);
  });
});
