import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { AiReviewRuleMutationFailure } from './useSaveAiReviewRuleMutation';
import { AiReviewRuleEditorDrawer } from './AiReviewRuleEditorDrawer';
import {
  AI_REVIEW_RULE_FORM_MESSAGES,
  buildAiReviewRuleRequest,
  submitAiReviewRuleForm,
  validateAiReviewRuleForm,
} from './aiReviewRuleFormModel';
import type { AiReviewRule } from './aiReviewRuleTypes';

vi.mock('@douyinfe/semi-icons', () => ({
  IconDelete: () => <span>delete</span>,
  IconPlus: () => <span>plus</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, disabled }: { children?: ReactNode; disabled?: boolean }) => (
    <button disabled={disabled}>{children}</button>
  ),
  Checkbox: ({ children, checked }: { children?: ReactNode; checked?: boolean }) => (
    <label><input type="checkbox" checked={Boolean(checked)} readOnly />{children}</label>
  ),
  Input: ({ placeholder, value }: { placeholder?: string; value?: string }) => (
    <input placeholder={placeholder} value={value} readOnly />
  ),
  InputNumber: ({ value }: { value?: string | number }) => <input aria-label="threshold" value={String(value ?? '')} readOnly />,
  SideSheet: ({ children, title, visible }: { children?: ReactNode; title?: ReactNode; visible?: boolean }) => (
    visible ? <aside aria-label={String(title)}>{children}</aside> : null
  ),
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
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
    useSaveAiReviewRuleMutation: () => ({ isPending: false, mutateAsync: vi.fn() }),
  };
});

vi.mock('./AutoPolicyPanel', () => ({
  AutoPolicyPanel: () => <section data-testid="auto-policy-panel" />,
}));

vi.mock('./AiReviewRuleHistoryPanel', () => ({
  AiReviewRuleHistoryPanel: () => (
    <section data-testid="ai-review-rule-history-section">
      <h2>版本历史</h2>
      <button>发布</button>
      <span>当前生效</span>
    </section>
  ),
}));

const EMPTY_ESCALATION = { disagreementThreshold: '', escalateLoopFlags: false };

describe('aiReviewRuleFormModel', () => {
  it('validates the optional ADR-020 disagreement threshold and maps escalation settings into the request', () => {
    const base = { promptTemplate: 'p', dimensions: ['准确性'], passThreshold: '0.6', rejectThreshold: '0.45' };
    expect(validateAiReviewRuleForm({ ...base, ...EMPTY_ESCALATION })).toEqual({});
    expect(validateAiReviewRuleForm({ ...base, disagreementThreshold: '0.5', escalateLoopFlags: false }).disagreementThreshold)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.disagreementOrder);
    expect(validateAiReviewRuleForm({ ...base, disagreementThreshold: 'x', escalateLoopFlags: false }).disagreementThreshold)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.thresholdRange);
    const result = buildAiReviewRuleRequest(22, { ...base, disagreementThreshold: '0.25', escalateLoopFlags: true });
    expect(result.ok && result.request).toMatchObject({ disagreementThreshold: 0.25, escalateLoopFlags: true });
  });

  it('validates backend-aligned required and duplicate messages', () => {
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: ' ', dimensions: ['准确性'], passThreshold: '0.8', rejectThreshold: '0.2' }).promptTemplate)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.promptRequired);
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: [], passThreshold: '0.8', rejectThreshold: '0.2' }).dimensions)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.dimensionsRequired);
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: ['准确性', ' '], passThreshold: '0.8', rejectThreshold: '0.2' }).dimensions)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.dimensionsRequired);
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: ['准确性', ' 准确性 '], passThreshold: '0.8', rejectThreshold: '0.2' }).dimensions)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.dimensionsDuplicate);
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: ['准确性'], passThreshold: '1.1', rejectThreshold: '0.2' }).passThreshold)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.thresholdRange);
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: ['准确性'], passThreshold: 'abc', rejectThreshold: '0.2' }).passThreshold)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.thresholdRange);
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: ['准确性'], passThreshold: '0.7', rejectThreshold: '0.7' }).rejectThreshold)
      .toBe(AI_REVIEW_RULE_FORM_MESSAGES.thresholdOrder);
  });

  it('accepts threshold boundaries and maps trimmed dimensions into a request', () => {
    expect(validateAiReviewRuleForm({ ...EMPTY_ESCALATION, promptTemplate: 'p', dimensions: ['准确性'], passThreshold: '1', rejectThreshold: '0' })).toEqual({});

    const result = buildAiReviewRuleRequest(22, {
      promptTemplate: '  review prompt  ',
      dimensions: [' 准确性 ', '完整性'],
      passThreshold: '0.75',
      rejectThreshold: '0.25',
      ...EMPTY_ESCALATION,
    });

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.request).toEqual({
        taskId: 22,
        promptTemplate: '  review prompt  ',
        dimensions: ['准确性', '完整性'],
        threshold: 0.75,
        passThreshold: 0.75,
        rejectThreshold: 0.25,
        disagreementThreshold: null,
        escalateLoopFlags: false,
      });
    }
  });

  it('submits through the provided save function and preserves backend user messages', async () => {
    const rule = makeRule({ versionNo: 3 });
    const save = vi.fn().mockResolvedValue(rule);

    await expect(submitAiReviewRuleForm(22, {
      promptTemplate: 'review prompt',
      dimensions: ['准确性'],
      passThreshold: '0.8',
      rejectThreshold: '0.2',
      ...EMPTY_ESCALATION,
    }, save)).resolves.toEqual({ ok: true, rule });
    expect(save).toHaveBeenCalledWith({
      taskId: 22,
      promptTemplate: 'review prompt',
      dimensions: ['准确性'],
      threshold: 0.8,
      passThreshold: 0.8,
      rejectThreshold: 0.2,
      disagreementThreshold: null,
      escalateLoopFlags: false,
    });

    const failedSave = vi.fn().mockRejectedValue(new AiReviewRuleMutationFailure(400, 'INVALID_AI_REVIEW_RULE', '评分维度不能重复'));
    await expect(submitAiReviewRuleForm(22, {
      promptTemplate: 'review prompt',
      dimensions: ['准确性'],
      passThreshold: '0.8',
      rejectThreshold: '0.2',
      ...EMPTY_ESCALATION,
    }, failedSave)).resolves.toEqual({ ok: false, errorMessage: '评分维度不能重复' });
  });
});

describe('AiReviewRuleEditorDrawer', () => {
  it('keeps publish, history, and current-state controls outside the save form body', () => {
    const html = renderToString(<AiReviewRuleEditorDrawer taskId={22} open onClose={() => {}} />);
    const formHtml = extractBetween(html, 'data-testid="ai-review-rule-save-form"', 'data-testid="ai-review-rule-history-section"');

    expect(html).toContain('AI 预审辅助配置');
    expect(formHtml).toContain('Prompt 模板');
    expect(formHtml).toContain('评分维度');
    expect(formHtml).toContain('通过阈值');
    expect(formHtml).toContain('打回阈值');
    expect(formHtml).toContain('保存草稿');
    expect(formHtml).not.toContain('发布');
    expect(formHtml).not.toContain('版本历史');
    expect(formHtml).not.toContain('当前生效');
    expect(html).toContain('data-testid="ai-review-rule-history-section"');
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
