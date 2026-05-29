import { beforeEach, describe, expect, it, vi } from 'vitest';
import { triggerAiReview } from './useTriggerAiReviewMutation';

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

describe('triggerAiReview', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('posts promptVersionId and omits the legacy promptVersion string', async () => {
    const result = {
      aiCall: {
        id: 9,
        submissionId: 300,
        purpose: 'submission_review',
        promptVersion: 'promptVersion#1',
        promptVersionId: 1,
        providerAdapterVersion: 'agent-default-v1',
        providerName: 'mock',
        modelName: 'mock-v1',
        inputHash: 'a'.repeat(64),
        status: 'completed',
        idempotencyKey: 'key',
        createdAt: '2026-05-28T00:00:00Z',
      },
      fieldFindings: [],
      overallSuggestion: 'pass',
      idempotencyHit: false,
    };
    postMock.mockResolvedValueOnce({ data: result, error: undefined, response: { status: 201 } });

    await expect(triggerAiReview({ submissionId: 300, promptVersionId: 1 })).resolves.toEqual(result);
    expect(postMock).toHaveBeenCalledWith('/submissions/{submissionId}/ai-review', {
      params: { path: { submissionId: 300 } },
      body: { promptVersionId: 1 },
    });
  });
});
