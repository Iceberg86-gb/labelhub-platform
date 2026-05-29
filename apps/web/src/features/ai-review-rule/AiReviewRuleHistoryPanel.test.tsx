import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AiReviewRuleMutationFailure } from './useSaveAiReviewRuleMutation';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { AiReviewRuleHistoryPanel, publishRuleAndRefresh } from './AiReviewRuleHistoryPanel';

const { invalidateQueriesMock, listState, publishMock } = vi.hoisted(() => ({
  invalidateQueriesMock: vi.fn(),
  listState: {
    data: [] as AiReviewRule[],
    error: null as Error | null,
    isError: false,
    isLoading: false,
  },
  publishMock: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: invalidateQueriesMock }),
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconTickCircle: () => <span>check</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, disabled }: { children?: ReactNode; disabled?: boolean }) => (
    <button disabled={disabled}>{children}</button>
  ),
  Empty: ({ title }: { title?: ReactNode }) => <div>{title}</div>,
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

vi.mock('./useListAiReviewRulesQuery', async () => {
  const actual = await vi.importActual<typeof import('./useListAiReviewRulesQuery')>('./useListAiReviewRulesQuery');
  return {
    ...actual,
    useListAiReviewRulesQuery: () => listState,
  };
});

vi.mock('./usePublishAiReviewRuleMutation', () => ({
  usePublishAiReviewRuleMutation: () => ({ isPending: false, mutateAsync: publishMock }),
}));

describe('AiReviewRuleHistoryPanel', () => {
  it('renders backend-provided current marker without deriving it from published status', () => {
    listState.data = [
      makeRule({ id: 1, versionNo: 1, status: 'published', isCurrent: false }),
      makeRule({ id: 2, versionNo: 2, status: 'draft', isCurrent: true }),
    ];
    listState.isLoading = false;
    listState.isError = false;
    listState.error = null;

    const html = renderToString(<AiReviewRuleHistoryPanel taskId={22} />);

    const text = html.replace(/<!-- -->/g, '');
    expect(text.indexOf('v1')).toBeLessThan(text.indexOf('v2'));
    expect(text).toContain('版本历史');
    expect(text).toContain('当前生效');
    expect(countOccurrences(text, '当前生效')).toBe(1);
    expect(text).toContain('发布');
  });

  it('publishes a selected rule and invalidates the task-scoped list query', async () => {
    publishMock.mockResolvedValueOnce(makeRule({ id: 2, versionNo: 2, status: 'published', isCurrent: true }));

    await publishRuleAndRefresh({
      taskId: 22,
      rule: makeRule({ id: 2, versionNo: 2 }),
      publishRule: publishMock,
      invalidateQueries: invalidateQueriesMock,
    });

    expect(publishMock).toHaveBeenCalledWith({ ruleId: 2 });
    expect(invalidateQueriesMock).toHaveBeenCalledWith({ queryKey: ['ai-review-rules', 22] });
  });

  it('keeps backend user messages when publish fails', async () => {
    publishMock.mockRejectedValueOnce(new AiReviewRuleMutationFailure(404, 'NOT_FOUND', 'AI 审核规则不存在'));

    await expect(publishRuleAndRefresh({
      taskId: 22,
      rule: makeRule({ id: 404, versionNo: 4 }),
      publishRule: publishMock,
      invalidateQueries: invalidateQueriesMock,
    })).rejects.toMatchObject({ userMessage: 'AI 审核规则不存在' });
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

function countOccurrences(value: string, needle: string): number {
  return value.split(needle).length - 1;
}
