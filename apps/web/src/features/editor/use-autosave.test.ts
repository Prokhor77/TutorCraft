import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAutosave } from './use-autosave';

const DEBOUNCE_MS = 500;
const INTERVAL_MS = 60_000;

function setup(save: (value: string) => Promise<unknown>, onError = vi.fn()) {
  const hook = renderHook(
    ({ value }) =>
      useAutosave({ value, save, debounceMs: DEBOUNCE_MS, intervalMs: INTERVAL_MS, onError }),
    { initialProps: { value: 'initial' } },
  );
  return { ...hook, onError };
}

describe('useAutosave', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('saves once after a pause in editing', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const { rerender, result } = setup(save);
    rerender({ value: 'a' });
    rerender({ value: 'ab' });
    await act(() => vi.advanceTimersByTimeAsync(DEBOUNCE_MS));
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('ab');
    expect(result.current.status).toBe('saved');
  });

  it('saves pending changes on unmount', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const { rerender, unmount } = setup(save);
    rerender({ value: 'pending' });
    unmount();
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(save).toHaveBeenCalledWith('pending');
  });

  it('reports a failure streak once and keeps retrying', async () => {
    const save = vi.fn().mockRejectedValue(new Error('offline'));
    const { rerender, result, onError } = setup(save);
    rerender({ value: 'x' });
    await act(() => vi.advanceTimersByTimeAsync(DEBOUNCE_MS));
    await act(() => vi.advanceTimersByTimeAsync(INTERVAL_MS));
    expect(save).toHaveBeenCalledTimes(2);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(result.current.status).toBe('error');
  });
});
