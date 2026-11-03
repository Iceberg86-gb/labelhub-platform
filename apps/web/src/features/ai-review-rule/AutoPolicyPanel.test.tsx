import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AiReviewAutoPolicyState, AiReviewRule, AiReviewRuleShadowStatsGroup } from './aiReviewRuleTypes';

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, disabled }: { children?: ReactNode; disabled?: boolean }) => <button disabled={disabled}>{children}</button>,
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => <div>{title}{description}</div>,
  Input: ({ placeholder, value }: { placeholder?: string; value?: string }) => <input placeholder={placeholder} value={value} readOnly />,
  Popconfirm: ({ children }: { children?: ReactNode }) => <>{children}</>,
  Select: ({ placeholder }: { placeholder?: string }) => <select aria-label={placeholder} />,
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => <span className={className}>{children}</span>,
  Toast: { success: vi.fn(), error: vi.fn() },
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
    Paragraph: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
  },
}));

const { AutoPolicyView, gateHint, toEnableRequest, validateAutoPolicyForm } = await import('./AutoPolicyPanel');

const rule = { id: 19, promptVersionId: 7, isCurrent: true, status: 'published' } as AiReviewRule;
const group = (over: Partial<AiReviewRuleShadowStatsGroup> = {}): AiReviewRuleShadowStatsGroup => ({
  promptVersionId: 7, modelName: 'gpt-6.1-terra', aiPassJudged: 280, humanRejected: 25, errorRate: '0.089286', wilsonUpper95: '0.128480', ...over,
});
const noop = () => {};
const view = (state?: AiReviewAutoPolicyState, groups = [group()], r = rule) => renderToString(
  <AutoPolicyView rule={r} shadowGroups={groups} state={state} enabling={false} disabling={false} gateError="" onEnable={noop} onDisable={noop} />,
);

describe('auto policy form model (ADR-020)', () => {
  it('requires a model, an owner-set error cap in (0, 0.5] and a sample rate in [0.05, 1]', () => {
    expect(validateAutoPolicyForm({ modelName: '', maxError: '', sampleRate: '0.10' })).toEqual({
      modelName: expect.any(String), maxError: expect.any(String),
    });
    expect(validateAutoPolicyForm({ modelName: 'm', maxError: '0.6', sampleRate: '0.01' })).toEqual({
      maxError: expect.any(String), sampleRate: expect.any(String),
    });
    expect(validateAutoPolicyForm({ modelName: 'm', maxError: '0.05', sampleRate: '0.10' })).toEqual({});
  });

  it('builds the enable request for the rule prompt with the 200-sample floor', () => {
    expect(toEnableRequest(7, { modelName: 'm', maxError: '0.05', sampleRate: '0.2' }))
      .toEqual({ promptVersionId: 7, modelName: 'm', maxError: 0.05, minSamples: 200, sampleRate: 0.2 });
  });

  it('hints the gate from the Wilson upper bound, not the observed rate', () => {
    expect(gateHint(group({ aiPassJudged: 150 }), '0.5')).toBe('insufficient');
    expect(gateHint(group(), '0.10')).toBe('too_high');   // 8.9% observed, but upper 12.8% > 10%
    expect(gateHint(group(), '0.13')).toBe('ok');
    expect(gateHint(group(), '')).toBe('unknown');
  });
});

describe('AutoPolicyView', () => {
  it('suggest mode shows shadow evidence per model and the enable form', () => {
    const html = view({ ruleId: 19, mode: 'suggest', events: [] });
    expect(html).toContain('仅建议');
    expect(html).toContain('gpt-6.1-terra');
    expect(html).toContain('12.8%');
    expect(html).toContain('开启自动通过');
    expect(html).not.toContain('关闭自动通过');
  });

  it('auto mode shows the running policy, live sample interval and the disable action', () => {
    const html = view({
      ruleId: 19,
      mode: 'auto',
      current: { id: 1, action: 'enable', actorType: 'owner', actorId: 1001, modelName: 'gpt-6.1-terra', maxError: '0.05', sampleRate: '0.10', evidence: {}, createdAt: '2026-11-03T10:00:00Z' },
      events: [{ id: 1, action: 'enable', actorType: 'owner', actorId: 1001, evidence: {}, createdAt: '2026-11-03T10:00:00Z' }],
      samples: { sampled: 12, judged: 10, humanRejected: 0, wilsonLower95: '0', wilsonUpper95: '0.277540', autoAccepted: 108 },
    });
    expect(html).toContain('自动通过 · 已开启');
    expect(html).toContain('已自动准入 <!-- -->108');
    expect(html).toContain('关闭自动通过');
    expect(html).toContain('Owner #1001');
  });

  it('a circuit break is shown as an alert with its reason', () => {
    const html = view({
      ruleId: 19,
      mode: 'suggest',
      current: { id: 2, action: 'circuit_break', actorType: 'system', evidence: {}, reason: '抽检错误率的 95% 置信下界 0.206549 超过上限 0.05', createdAt: '2026-11-03T11:00:00Z' },
      events: [],
    });
    expect(html).toContain('role="alert"');
    expect(html).toContain('已熔断');
    expect(html).toContain('0.206549');
  });

  it('a non-current rule cannot be enabled and an empty shadow set is explained', () => {
    const html = view({ ruleId: 19, mode: 'suggest', events: [] }, [], { ...rule, isCurrent: false });
    expect(html).toContain('只有任务当前生效的已发布规则版本可以开启自动通过');
    expect(html).toContain('暂无影子证据');
  });
});
