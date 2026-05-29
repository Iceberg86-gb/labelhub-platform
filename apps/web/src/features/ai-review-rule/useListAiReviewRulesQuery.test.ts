import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AiReviewRuleQueryError, aiReviewRulesQueryKey, fetchAiReviewRules } from './useListAiReviewRulesQuery';
import type { AiReviewRule } from './aiReviewRuleTypes';

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    GET: getMock,
  },
}));

describe('fetchAiReviewRules', () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  it('fetches task-scoped AI review rules through the list endpoint', async () => {
    const rules = [makeRule({ versionNo: 1 }), makeRule({ id: 12, versionNo: 2, status: 'published', isCurrent: true })];
    getMock.mockResolvedValueOnce({ data: rules, error: undefined, response: { status: 200 } });

    await expect(fetchAiReviewRules(22)).resolves.toEqual(rules);
    expect(getMock).toHaveBeenCalledWith('/ai-review/rules', {
      params: { query: { taskId: 22 } },
    });
    expect(aiReviewRulesQueryKey(22)).toEqual(['ai-review-rules', 22]);
  });

  it('throws a query-specific failure instead of a mutation failure', async () => {
    getMock.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'TASK_NOT_FOUND', message: '任务不存在' },
      response: { status: 404 },
    });

    try {
      await fetchAiReviewRules(404);
      throw new Error('Expected fetchAiReviewRules to reject');
    } catch (error) {
      expect(error).toBeInstanceOf(AiReviewRuleQueryError);
      expect(error).toMatchObject({
        status: 404,
        code: 'TASK_NOT_FOUND',
        userMessage: '任务不存在',
      });
    }
  });
});

function makeRule(overrides: Partial<AiReviewRule> = {}): AiReviewRule {
  return {
    id: 11,
    taskId: 22,
    versionNo: 1,
    promptVersionId: 3,
    promptTemplate: 'review prompt',
    dimensions: ['准确性'],
    threshold: 0.8,
    status: 'draft',
    isCurrent: false,
    createdAt: '2026-05-28T00:00:00Z',
    activatedAt: null,
    ...overrides,
  };
}
