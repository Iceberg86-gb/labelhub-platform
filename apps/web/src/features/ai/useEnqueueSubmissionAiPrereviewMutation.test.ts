import { beforeEach, describe, expect, it, vi } from 'vitest';
import { enqueueSubmissionAiPrereview } from './useEnqueueSubmissionAiPrereviewMutation';

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

describe('enqueueSubmissionAiPrereview', () => {
  beforeEach(() => {
    postMock.mockReset();
  });

  it('posts the submission to the asynchronous AI prereview queue', async () => {
    const result = {
      taskId: 44,
      enqueuedCount: 1,
      skippedCount: 0,
      summary: {
        taskId: 44,
        totalCount: 10,
        pendingCount: 9,
        processingCount: 1,
        completedCount: 0,
        failedCount: 0,
        enqueueableCount: 9,
      },
    };
    postMock.mockResolvedValueOnce({ data: result, error: undefined, response: { status: 202 } });

    await expect(enqueueSubmissionAiPrereview({ submissionId: 300 })).resolves.toEqual(result);
    expect(postMock).toHaveBeenCalledWith('/submissions/{submissionId}/ai-prereview/enqueue', {
      params: { path: { submissionId: 300 } },
    });
  });
});
