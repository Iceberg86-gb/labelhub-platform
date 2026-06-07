import { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { renderClient } from './formily/__tests__/renderClient';
import { useAutosave } from './useAutosave';

describe('useAutosave', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('clears the saved status as soon as a later value becomes pending', async () => {
    vi.useFakeTimers();
    const onSave = vi.fn(async () => {});

    const view = renderClient(<AutosaveProbe value={{ attachment: 'initial' }} onSave={onSave} />);
    view.rerender(<AutosaveProbe value={{ attachment: 'saved' }} onSave={onSave} />);

    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(view.text()).toBe('saved');
    expect(onSave).toHaveBeenLastCalledWith({ attachment: 'saved' });

    view.rerender(<AutosaveProbe value={{ attachment: 'pending' }} onSave={onSave} />);

    expect(view.text()).toBe('idle');
    expect(onSave).toHaveBeenCalledTimes(1);
    view.unmount();
  });
});

function AutosaveProbe({
  value,
  onSave,
}: {
  value: AnswerPayload;
  onSave: (value: AnswerPayload) => Promise<void>;
}) {
  const autosave = useAutosave({ value, onSave, debounceMs: 1_000, maxWaitMs: 5_000 });
  return <span>{autosave.status}</span>;
}
