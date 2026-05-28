import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { SubmitValidationError, submitSession } from './useSubmitMutation';

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

const input = {
  sessionId: 42,
  answerPayload: { field_0: 'ab' } satisfies AnswerPayload,
};

describe('submitSession', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('preserves backend 422 fieldErrors as SubmitValidationError', async () => {
    const fieldErrors = [{ field: 'field_0', message: '最少 5 字' }];
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: {
        code: 'VALIDATION_FAILED',
        message: 'Answer payload failed validation',
        fieldErrors,
      },
      response: { status: 422 },
    });

    let thrown: unknown;
    try {
      await submitSession(input);
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeInstanceOf(SubmitValidationError);
    expect((thrown as SubmitValidationError).fieldErrors).toEqual(fieldErrors);
    expect((thrown as Error).message).toBe('Answer payload failed validation');
  });

  it('keeps non-validation submit failures as plain Error', async () => {
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'INTERNAL_ERROR', message: '服务暂不可用' },
      response: { status: 500 },
    });

    await expect(submitSession(input)).rejects.toEqual(new Error('服务暂不可用'));
  });
});
