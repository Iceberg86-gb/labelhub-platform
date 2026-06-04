import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { AiReviewRuleEntryCard } from './AiReviewRuleEntryCard';
import { publishAiReviewRule } from './usePublishAiReviewRuleMutation';
import { saveAiReviewRule } from './useSaveAiReviewRuleMutation';

const { listState, postMock } = vi.hoisted(() => ({
  listState: {
    data: [] as AiReviewRule[],
    error: null as Error | null,
    isError: false,
    isLoading: false,
  },
  postMock: vi.fn(),
}));

vi.mock('../../shared/api/client', () => ({
  apiClient: {
    POST: postMock,
  },
}));

vi.mock('./useListAiReviewRulesQuery', () => ({
  aiReviewRulesQueryKey: (taskId: number) => ['ai-review-rules', taskId],
  useListAiReviewRulesQuery: () => listState,
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconEdit: () => <span>edit</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, disabled }: { children?: ReactNode; disabled?: boolean }) => (
    <button disabled={disabled}>{children}</button>
  ),
  Card: ({ children }: { children?: ReactNode }) => <section>{children}</section>,
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

describe('AI review rule C1 hooks', () => {
  beforeEach(() => {
    postMock.mockReset();
    listState.data = [];
    listState.error = null;
    listState.isError = false;
    listState.isLoading = false;
  });

  it('posts an append-only AI review rule request', async () => {
    const rule = makeRule({ status: 'draft' });
    postMock.mockResolvedValueOnce({ data: rule, error: undefined, response: { status: 201 } });

    await expect(
      saveAiReviewRule({
        taskId: 22,
        promptTemplate: 'review prompt',
        dimensions: ['准确性'],
        threshold: 0.8,
        passThreshold: 0.8,
        rejectThreshold: 0.2,
      }),
    ).resolves.toEqual(rule);
    expect(postMock).toHaveBeenCalledWith('/ai-review/rules', {
      body: {
        taskId: 22,
        promptTemplate: 'review prompt',
        dimensions: ['准确性'],
        threshold: 0.8,
        passThreshold: 0.8,
        rejectThreshold: 0.2,
      },
    });
  });

  it('maps validation failures as expected business errors', async () => {
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'INVALID_AI_REVIEW_RULE', message: '评分维度不能重复' },
      response: { status: 400 },
    });

    await expect(
      saveAiReviewRule({
        taskId: 22,
        promptTemplate: 'review prompt',
        dimensions: ['准确性', '准确性'],
        threshold: 0.8,
        passThreshold: 0.8,
        rejectThreshold: 0.2,
      }),
    ).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_AI_REVIEW_RULE',
      userMessage: '评分维度不能重复',
    });
  });

  it('posts publish to the rule activation endpoint', async () => {
    const rule = makeRule({ status: 'published', activatedAt: '2026-05-28T00:01:00Z' });
    postMock.mockResolvedValueOnce({ data: rule, error: undefined, response: { status: 200 } });

    await expect(publishAiReviewRule({ ruleId: 11 })).resolves.toEqual(rule);
    expect(postMock).toHaveBeenCalledWith('/ai-review/rules/{ruleId}/publish', {
      params: { path: { ruleId: 11 } },
    });
  });

  it('maps missing rules to a user-facing 404 message', async () => {
    postMock.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'NOT_FOUND', message: 'AI 审核规则不存在' },
      response: { status: 404 },
    });

    await expect(publishAiReviewRule({ ruleId: 404 })).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
      userMessage: 'AI 审核规则不存在',
    });
  });
});

describe('AiReviewRuleEntryCard', () => {
  it('renders the append-only entry without a form body', () => {
    const html = renderToString(<AiReviewRuleEntryCard taskId={22} onOpenEditor={() => {}} />);

    expect(html).toContain('AI 预审辅助规则');
    expect(html).toContain('配置规则');
    expect(html).toContain('未配置 AI 预审');
    expect(html).toContain('保存会创建新的规则版本');
    expect(html).toContain('发布后才会成为当前生效规则');
    expect(html).not.toContain('promptTemplate');
    expect(html).not.toContain('textarea');
  });

  it('can render a disabled placeholder while the future editor mount is unavailable', () => {
    const html = renderToString(<AiReviewRuleEntryCard taskId={22} onOpenEditor={() => {}} disabled />);

    expect(html).toContain('配置规则');
    expect(html).toContain('button disabled');
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
    passThreshold: 0.8,
    rejectThreshold: 0.2,
    status: 'draft' as const,
    isCurrent: false,
    createdAt: '2026-05-28T00:00:00Z',
    activatedAt: null,
    ...overrides,
  };
}
