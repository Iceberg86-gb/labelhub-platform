import { describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { SubmitValidationError } from '../../features/labeling/useSubmitMutation';
import type { OfflineDraftSubmitPreSyncResult } from './useOfflineDraftSync';
import { runLabelerSubmitWithOfflineDraft } from '../../pages/labeler/labelerSubmitOfflineDraftFlow';

const finalPayload = { field_0: 'in-memory' } satisfies AnswerPayload;
const localPendingPayload = { field_0: 'stale-local' } satisfies AnswerPayload;

describe('runLabelerSubmitWithOfflineDraft', () => {
  it('stores the in-memory payload, pre-syncs it, then submits that same in-memory payload', async () => {
    const order: string[] = [];
    const deps = createDeps({
      flush: vi.fn(async () => {
        order.push('flush');
      }),
      disable: vi.fn(() => {
        order.push('disable');
      }),
      submit: vi.fn().mockResolvedValue({ id: 99 }),
      preSync: vi.fn().mockResolvedValue({ kind: 'synced' } satisfies OfflineDraftSubmitPreSyncResult),
    });

    await runLabelerSubmitWithOfflineDraft(deps);

    expect(order).toEqual(['flush', 'disable']);
    expect(deps.bufferPending).toHaveBeenCalledWith({
      userId: 10,
      sessionId: 20,
      schemaVersionId: 30,
      payload: finalPayload,
    });
    expect(deps.preSync).toHaveBeenCalledWith(20);
    expect(deps.submit).toHaveBeenCalledWith(finalPayload);
    expect(deps.submit).not.toHaveBeenCalledWith(localPendingPayload);
    expect(deps.clearPending).toHaveBeenCalledWith(20);
    expect(deps.onSuccess).toHaveBeenCalledWith({ id: 99 });
  });

  it.each([
    ['auth', '登录状态或权限已失效,请重新登录后再提交'],
    ['not_found', '会话不可用,请刷新后重试'],
    ['terminal', '此会话已在别处提交/释放,本地草稿已弃'],
  ] as const)('blocks submit when pre-sync returns %s', async (reason, message) => {
    const deps = createDeps({
      preSync: vi.fn().mockResolvedValue({
        kind: 'block-submit',
        reason,
        message,
      } satisfies OfflineDraftSubmitPreSyncResult),
    });

    await runLabelerSubmitWithOfflineDraft(deps);

    expect(deps.submit).not.toHaveBeenCalled();
    expect(deps.clearPending).not.toHaveBeenCalled();
    expect(deps.onBlocked).toHaveBeenCalledWith(message);
  });

  it.each([
    { kind: 'continue-with-pending', reason: 'network' },
    { kind: 'continue-with-pending', reason: 'server' },
    { kind: 'continue-with-pending', reason: 'bad_request' },
    { kind: 'storage-unavailable' },
    { kind: 'missing-user' },
    { kind: 'no-pending' },
  ] satisfies OfflineDraftSubmitPreSyncResult[])('allows submit after non-blocking pre-sync result %#', async (result) => {
    const deps = createDeps({
      preSync: vi.fn().mockResolvedValue(result),
      submit: vi.fn().mockResolvedValue({ id: 101 }),
    });

    await runLabelerSubmitWithOfflineDraft(deps);

    expect(deps.submit).toHaveBeenCalledWith(finalPayload);
    expect(deps.onSuccess).toHaveBeenCalledWith({ id: 101 });
  });

  it('keeps pending and preserves P3a validation handling on submit 422', async () => {
    const error = new SubmitValidationError([{ field: 'field_0', message: '必填' }]);
    const deps = createDeps({
      submit: vi.fn().mockRejectedValue(error),
    });

    await runLabelerSubmitWithOfflineDraft(deps);

    expect(deps.clearPending).not.toHaveBeenCalled();
    expect(deps.onValidationError).toHaveBeenCalledWith(error);
    expect(deps.onGenericError).not.toHaveBeenCalled();
  });

  it('keeps pending on generic submit failure', async () => {
    const deps = createDeps({
      submit: vi.fn().mockRejectedValue(new Error('down')),
    });

    await runLabelerSubmitWithOfflineDraft(deps);

    expect(deps.clearPending).not.toHaveBeenCalled();
    expect(deps.onGenericError).toHaveBeenCalledWith(expect.any(Error));
  });

  it('falls back to pure submit when userId is missing', async () => {
    const deps = createDeps({ userId: null });

    await runLabelerSubmitWithOfflineDraft(deps);

    expect(deps.bufferPending).not.toHaveBeenCalled();
    expect(deps.preSync).not.toHaveBeenCalled();
    expect(deps.submit).toHaveBeenCalledWith(finalPayload);
  });
});

function createDeps(overrides: Partial<Parameters<typeof runLabelerSubmitWithOfflineDraft>[0]> = {}) {
  return {
    sessionId: 20,
    userId: 10,
    schemaVersionId: 30,
    finalPayload,
    flush: vi.fn().mockResolvedValue(undefined),
    disable: vi.fn(),
    bufferPending: vi.fn().mockResolvedValue({ buffered: true, savedAt: 1_000 }),
    preSync: vi.fn().mockResolvedValue({ kind: 'synced' } satisfies OfflineDraftSubmitPreSyncResult),
    submit: vi.fn().mockResolvedValue({ id: 88 }),
    clearPending: vi.fn().mockResolvedValue(undefined),
    onSuccess: vi.fn(),
    onBlocked: vi.fn(),
    onValidationError: vi.fn(),
    onGenericError: vi.fn(),
    ...overrides,
  };
}
