import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { submitTaskDrafts } from './useSubmitTaskDraftsMutation';

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

describe('submitTaskDrafts', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('posts the current session payload to the task draft batch endpoint', async () => {
    const result = {
      submittedCount: 3,
      submissions: [{ id: 11 }, { id: 12 }, { id: 13 }],
    };
    postMock.mockResolvedValueOnce({ data: result, error: undefined, response: { status: 201 } });

    await expect(submitTaskDrafts({
      taskId: 22,
      currentSessionId: 13,
      answerPayload: { field_0: 'final answer' } satisfies AnswerPayload,
    })).resolves.toEqual(result);

    expect(postMock).toHaveBeenCalledWith('/tasks/{taskId}/submit-drafts', {
      params: { path: { taskId: 22 } },
      body: {
        currentSessionId: 13,
        answerPayload: { field_0: 'final answer' },
      },
    });
  });

  it('keeps backend failures as plain errors', async () => {
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'DRAFT_NOT_FOUND', message: '还有题目未保存草稿' },
      response: { status: 404 },
    });

    await expect(submitTaskDrafts({
      taskId: 22,
      currentSessionId: 13,
      answerPayload: { field_0: 'final answer' },
    })).rejects.toEqual(new Error('还有题目未保存草稿'));
  });
});
