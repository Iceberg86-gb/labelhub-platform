import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { AiReviewRuleEditorDrawer } from './AiReviewRuleEditorDrawer';

const { invalidateQueriesMock, listState, publishMock, saveMock } = vi.hoisted(() => ({
  invalidateQueriesMock: vi.fn(),
  listState: {
    data: [] as AiReviewRule[],
    error: null as Error | null,
    isError: false,
    isLoading: false,
  },
  publishMock: vi.fn(),
  saveMock: vi.fn(),
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: invalidateQueriesMock }),
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconDelete: () => <span>delete</span>,
  IconPlus: () => <span>plus</span>,
  IconTickCircle: () => <span>check</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, disabled }: { children?: ReactNode; disabled?: boolean }) => (
    <button disabled={disabled}>{children}</button>
  ),
  Empty: ({ title }: { title?: ReactNode }) => <div>{title}</div>,
  Input: ({ placeholder, value }: { placeholder?: string; value?: string }) => (
    <input placeholder={placeholder} value={value} readOnly />
  ),
  InputNumber: ({ value }: { value?: string | number }) => <input aria-label="threshold" value={String(value ?? '')} readOnly />,
  SideSheet: ({ children, title, visible }: { children?: ReactNode; title?: ReactNode; visible?: boolean }) => (
    visible ? <aside aria-label={String(title)}>{children}</aside> : null
  ),
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  TextArea: ({ placeholder, value }: { placeholder?: string; value?: string }) => (
    <textarea placeholder={placeholder} value={value} readOnly />
  ),
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
  Typography: {
    Paragraph: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

vi.mock('./useSaveAiReviewRuleMutation', async () => {
  const actual = await vi.importActual<typeof import('./useSaveAiReviewRuleMutation')>('./useSaveAiReviewRuleMutation');
  return {
    ...actual,
    useSaveAiReviewRuleMutation: () => ({ isPending: false, mutateAsync: saveMock }),
  };
});

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

describe('AiReviewRule editor integration', () => {
  it('keeps save-form controls separate from history and current-state controls', () => {
    listState.data = [
      makeRule({ id: 1, versionNo: 1, status: 'published', isCurrent: false }),
      makeRule({ id: 2, versionNo: 2, status: 'draft', isCurrent: true }),
      makeRule({ id: 3, versionNo: 3, status: 'draft', isCurrent: false }),
    ];
    listState.isError = false;
    listState.isLoading = false;
    listState.error = null;

    const html = renderToString(<AiReviewRuleEditorDrawer taskId={22} open onClose={() => {}} />);
    const formHtml = extractBetween(html, 'data-testid="ai-review-rule-save-form"', 'data-testid="ai-review-rule-history-section"');
    const historyHtml = stripReactComments(html.slice(html.indexOf('data-testid="ai-review-rule-history-section"')));

    expect(formHtml).toContain('Prompt 模板');
    expect(formHtml).toContain('保存草稿');
    expect(formHtml).not.toContain('发布');
    expect(formHtml).not.toContain('当前生效');

    expect(historyHtml).toContain('版本历史');
    expect(historyHtml).toContain('v1');
    expect(historyHtml).toContain('v2');
    expect(historyHtml).toContain('v3');
    expect(historyHtml).toContain('当前生效');
    expect(historyHtml).toContain('发布');
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

function extractBetween(value: string, start: string, end: string): string {
  const startIndex = value.indexOf(start);
  const endIndex = value.indexOf(end);
  expect(startIndex).toBeGreaterThanOrEqual(0);
  expect(endIndex).toBeGreaterThan(startIndex);
  return value.slice(startIndex, endIndex);
}

function stripReactComments(value: string): string {
  return value.replace(/<!--\s*-->/g, '');
}
