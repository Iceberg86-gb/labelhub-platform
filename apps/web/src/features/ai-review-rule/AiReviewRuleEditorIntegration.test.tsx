import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { AiReviewRuleEditorDrawer } from './AiReviewRuleEditorDrawer';
import { AiReviewRuleEntryCard } from './AiReviewRuleEntryCard';

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
  IconEdit: () => <span>edit</span>,
  IconPlus: () => <span>plus</span>,
  IconTickCircle: () => <span>check</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className, disabled }: { children?: ReactNode; className?: string; disabled?: boolean }) => (
    <button className={className} disabled={disabled}>{children}</button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <section className={className}>{children}</section>
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
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => <span className={className}>{children}</span>,
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
  beforeEach(() => {
    invalidateQueriesMock.mockReset();
    publishMock.mockReset();
    saveMock.mockReset();
    listState.data = [];
    listState.error = null;
    listState.isError = false;
    listState.isLoading = false;
  });

  it('renders the entry card as an unconfigured AI pre-review module', () => {
    const html = stripReactComments(renderToString(<AiReviewRuleEntryCard taskId={22} onOpenEditor={() => {}} />));

    expect(html).toContain('ai-review-rule-entry-card ai-review-rule-entry-card--assistive');
    expect(html).toContain('ai-review-rule-entry-hero');
    expect(html).toContain('ai-review-rule-task-tag');
    expect(html).toContain('ai-review-rule-status-tag ai-review-rule-status-tag--draft');
    expect(html).toContain('AI 预审辅助规则');
    expect(html).toContain('未配置 AI 预审');
    expect(html).toContain('配置规则');
  });

  it('renders the current published rule summary in the entry card', () => {
    listState.data = [
      makeRule({
        id: 1,
        versionNo: 4,
        promptVersionId: 12,
        dimensions: ['准确性', '安全性'],
        passThreshold: 0.75,
        rejectThreshold: 0.25,
        status: 'published',
        isCurrent: true,
      }),
      makeRule({ id: 2, versionNo: 5, status: 'draft', isCurrent: false }),
    ];

    const html = stripReactComments(renderToString(<AiReviewRuleEntryCard taskId={22} onOpenEditor={() => {}} />));

    expect(html).toContain('生效中 · v4');
    expect(html).toContain('Prompt 版本');
    expect(html).toContain('#12');
    expect(html).toContain('准确性');
    expect(html).toContain('安全性');
    expect(html).toContain('通过阈值');
    expect(html).toContain('0.75');
    expect(html).toContain('拒绝阈值');
    expect(html).toContain('0.25');
    expect(html).toContain('查看 / 编辑规则');
  });

  it('keeps the entry card usable while the rule query is loading or failed', () => {
    listState.isLoading = true;
    const loadingHtml = renderToString(<AiReviewRuleEntryCard taskId={22} onOpenEditor={() => {}} />);

    expect(loadingHtml).toContain('未配置 AI 预审');
    expect(loadingHtml).toContain('配置规则');
    expect(loadingHtml).toContain('保存会创建新的规则版本');
    expect(loadingHtml).not.toContain('生效中');

    listState.isLoading = false;
    listState.isError = true;
    listState.error = new Error('boom');
    const errorHtml = renderToString(<AiReviewRuleEntryCard taskId={22} onOpenEditor={() => {}} />);

    expect(errorHtml).toContain('未配置 AI 预审');
    expect(errorHtml).toContain('配置规则');
    expect(errorHtml).toContain('发布后才会成为当前生效规则');
    expect(errorHtml).not.toContain('LLM 生成失败');
  });

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

  it('renders the AI review configuration as assistive evidence tooling instead of final decision controls', () => {
    listState.data = [
      makeRule({ id: 1, versionNo: 1, status: 'published', isCurrent: true }),
      makeRule({ id: 2, versionNo: 2, status: 'draft', isCurrent: false }),
    ];
    listState.isError = false;
    listState.isLoading = false;
    listState.error = null;

    const html = renderToString(<AiReviewRuleEditorDrawer taskId={22} open onClose={() => {}} />);

    expect(html).toContain('ai-review-rule-editor ai-review-rule-editor--drawer');
    expect(html).toContain('ai-review-rule-save-form ai-review-rule-save-form--structured');
    expect(html).toContain('ai-review-rule-assistive-note');
    expect(html).toContain('ai-review-rule-history ai-review-rule-history--versioned');
    expect(html).toContain('ai-review-rule-status-tag ai-review-rule-status-tag--published');
    expect(html).toContain('ai-review-rule-current-tag');
    expect(html).toContain('AI 预审辅助配置');
    expect(html).toContain('AI 只提供预审证据');
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
