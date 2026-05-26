import { useCallback, useEffect, useRef, useState } from 'react';
import type { AnswerPayload } from '../../entities/submission/answerPayload';

type AutosaveStatus = 'idle' | 'saving' | 'saved' | 'error';

export interface UseAutosaveOptions {
  value: AnswerPayload;
  onSave: (value: AnswerPayload) => Promise<void>;
  debounceMs?: number;
  maxWaitMs?: number;
  enabled?: boolean;
}

export interface UseAutosaveResult {
  status: AutosaveStatus;
  lastSavedAt: number | null;
  lastError: Error | null;
  flush: () => Promise<void>;
  disable: () => void;
}

export function useAutosave({
  value,
  onSave,
  debounceMs = 3_000,
  maxWaitMs = 15_000,
  enabled = true,
}: UseAutosaveOptions): UseAutosaveResult {
  const valueRef = useRef(value);
  const onSaveRef = useRef(onSave);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const maxWaitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const disabledRef = useRef(!enabled);
  const mountedRef = useRef(false);
  const saveRunRef = useRef(0);

  const [status, setStatus] = useState<AutosaveStatus>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<number | null>(null);
  const [lastError, setLastError] = useState<Error | null>(null);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    onSaveRef.current = onSave;
  }, [onSave]);

  const clearTimers = useCallback(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    if (maxWaitTimerRef.current) {
      clearTimeout(maxWaitTimerRef.current);
      maxWaitTimerRef.current = null;
    }
  }, []);

  const saveNow = useCallback(async () => {
    if (disabledRef.current) return;

    clearTimers();
    const runId = saveRunRef.current + 1;
    saveRunRef.current = runId;
    setStatus('saving');
    setLastError(null);

    try {
      await onSaveRef.current(valueRef.current);
      if (disabledRef.current || saveRunRef.current !== runId) return;
      setLastSavedAt(Date.now());
      setStatus('saved');
    } catch (error) {
      if (disabledRef.current || saveRunRef.current !== runId) return;
      setLastError(error instanceof Error ? error : new Error('自动保存失败'));
      setStatus('error');
    }
  }, [clearTimers]);

  const scheduleSave = useCallback(() => {
    if (disabledRef.current) return;

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    debounceTimerRef.current = setTimeout(() => {
      void saveNow();
    }, debounceMs);

    if (!maxWaitTimerRef.current) {
      maxWaitTimerRef.current = setTimeout(() => {
        void saveNow();
      }, maxWaitMs);
    }
  }, [debounceMs, maxWaitMs, saveNow]);

  useEffect(() => {
    disabledRef.current = !enabled;
    if (!enabled) {
      clearTimers();
    }
  }, [clearTimers, enabled]);

  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      return;
    }
    scheduleSave();
  }, [scheduleSave, value]);

  useEffect(() => clearTimers, [clearTimers]);

  const flush = useCallback(async () => {
    await saveNow();
  }, [saveNow]);

  const disable = useCallback(() => {
    disabledRef.current = true;
    clearTimers();
  }, [clearTimers]);

  return {
    status,
    lastSavedAt,
    lastError,
    flush,
    disable,
  };
}
