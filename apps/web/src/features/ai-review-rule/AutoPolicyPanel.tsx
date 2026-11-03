import { Button, Input, Popconfirm, Select, Toast, Typography } from '@douyinfe/semi-ui';
import { useMemo, useState } from 'react';
import { EmptyState, SectionCard, StatusBadge } from '../../shared/ui';
import type {
  AiReviewAutoPolicyEnableRequest,
  AiReviewAutoPolicyEvent,
  AiReviewAutoPolicyState,
  AiReviewRule,
  AiReviewRuleShadowStatsGroup,
} from './aiReviewRuleTypes';
import {
  AutoPolicyFailure,
  useAutoPolicyQuery,
  useDisableAutoPolicyMutation,
  useEnableAutoPolicyMutation,
  useShadowStatsQuery,
} from './useAutoPolicy';
import { useListAiReviewRulesQuery } from './useListAiReviewRulesQuery';

/** Gate defaults mirrored from the API (AutoPolicyService); the server stays authoritative. */
export const AUTO_POLICY_MIN_SAMPLES = 200;
export const AUTO_POLICY_DEFAULT_SAMPLE_RATE = '0.10';

const EVENT_LABELS: Record<AiReviewAutoPolicyEvent['action'], string> = {
  enable: '开启',
  disable: '关闭',
  circuit_break: '熔断',
};

export type AutoPolicyFormState = { modelName: string; maxError: string; sampleRate: string };
export type AutoPolicyFormErrors = Partial<Record<keyof AutoPolicyFormState, string>>;

export function validateAutoPolicyForm(form: AutoPolicyFormState): AutoPolicyFormErrors {
  const errors: AutoPolicyFormErrors = {};
  if (!form.modelName) errors.modelName = '请选择门槛证据对应的模型';
  const maxError = Number(form.maxError);
  if (form.maxError.trim() === '' || !Number.isFinite(maxError) || maxError <= 0 || maxError > 0.5) {
    errors.maxError = '错误率上限必须在 (0, 0.5] 之间';
  }
  const sampleRate = Number(form.sampleRate);
  if (!Number.isFinite(sampleRate) || sampleRate < 0.05 || sampleRate > 1) {
    errors.sampleRate = '抽检比例必须在 [0.05, 1] 之间';
  }
  return errors;
}

export function toEnableRequest(promptVersionId: number, form: AutoPolicyFormState): AiReviewAutoPolicyEnableRequest {
  return {
    promptVersionId,
    modelName: form.modelName,
    maxError: Number(form.maxError),
    minSamples: AUTO_POLICY_MIN_SAMPLES,
    sampleRate: Number(form.sampleRate),
  };
}

/** Client-side hint only: does this shadow group look like it would pass the gate at the given cap? */
export function gateHint(group: AiReviewRuleShadowStatsGroup, maxError: string): 'insufficient' | 'too_high' | 'ok' | 'unknown' {
  if (group.aiPassJudged < AUTO_POLICY_MIN_SAMPLES) return 'insufficient';
  const cap = Number(maxError);
  if (maxError.trim() === '' || !Number.isFinite(cap) || group.wilsonUpper95 == null) return 'unknown';
  return Number(group.wilsonUpper95) <= cap ? 'ok' : 'too_high';
}

function pct(value: string | null | undefined): string {
  if (value == null) return '-';
  const n = Number(value);
  return Number.isFinite(n) ? `${(n * 100).toFixed(1)}%` : '-';
}

type AutoPolicyViewProps = {
  rule: AiReviewRule;
  shadowGroups: AiReviewRuleShadowStatsGroup[];
  state: AiReviewAutoPolicyState | undefined;
  enabling: boolean;
  disabling: boolean;
  gateError: string;
  onEnable: (request: AiReviewAutoPolicyEnableRequest) => void;
  onDisable: () => void;
};

/** Presentational: everything the owner needs to decide and be accountable for auto-pass on this rule version. */
export function AutoPolicyView({ rule, shadowGroups, state, enabling, disabling, gateError, onEnable, onDisable }: AutoPolicyViewProps) {
  const groups = useMemo(() => shadowGroups.filter((g) => g.promptVersionId === rule.promptVersionId), [shadowGroups, rule.promptVersionId]);
  const [form, setForm] = useState<AutoPolicyFormState>({ modelName: '', maxError: '', sampleRate: AUTO_POLICY_DEFAULT_SAMPLE_RATE });
  const [errors, setErrors] = useState<AutoPolicyFormErrors>({});
  const isAuto = state?.mode === 'auto';
  const lastBreak = state?.current?.action === 'circuit_break' ? state.current : undefined;

  const submit = () => {
    const nextErrors = validateAutoPolicyForm(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length === 0) onEnable(toEnableRequest(rule.promptVersionId, form));
  };

  return (
    <SectionCard
      className="auto-policy-panel"
      title="自动通过（策略准入）"
      subtitle="满足门槛后,AI 判通过的提交可跳过初审直接准入;由开启它的 Owner 承担问责。"
      actions={<StatusBadge tone={isAuto ? 'warning' : 'neutral'}>{isAuto ? '自动通过 · 已开启' : '仅建议'}</StatusBadge>}
    >
      {!rule.isCurrent || rule.status !== 'published' ? (
        <Typography.Text type="tertiary">只有任务当前生效的已发布规则版本可以开启自动通过。</Typography.Text>
      ) : null}

      {lastBreak ? (
        <div className="auto-policy-panel__alert" role="alert">
          <StatusBadge tone="danger">已熔断</StatusBadge>
          <Typography.Text>{lastBreak.reason ?? '抽检错误率超过上限,已自动退回仅建议模式。'}</Typography.Text>
        </div>
      ) : null}

      <div className="auto-policy-panel__section">
        <Typography.Text strong>影子证据（suggest 模式下 AI 判通过、且已有人工裁决）</Typography.Text>
        {groups.length === 0 ? (
          <EmptyState variant="inline" title="暂无影子证据" description="规则在仅建议模式下运行、并积累人工裁决后,这里会出现按模型统计的错误率。" />
        ) : (
          <table className="auto-policy-panel__table">
            <thead>
              <tr><th>模型</th><th>样本</th><th>人工打回</th><th>错误率</th><th>95% 上界</th><th>门槛</th></tr>
            </thead>
            <tbody>
              {groups.map((g) => {
                const hint = gateHint(g, form.maxError);
                return (
                  <tr key={g.modelName}>
                    <td className="mono-value">{g.modelName}</td>
                    <td>{g.aiPassJudged}</td>
                    <td>{g.humanRejected}</td>
                    <td>{pct(g.errorRate)}</td>
                    <td>{pct(g.wilsonUpper95)}</td>
                    <td>
                      {hint === 'insufficient' ? <StatusBadge tone="neutral" size="small">样本不足 {AUTO_POLICY_MIN_SAMPLES}</StatusBadge> : null}
                      {hint === 'too_high' ? <StatusBadge tone="danger" size="small">上界超过上限</StatusBadge> : null}
                      {hint === 'ok' ? <StatusBadge tone="success" size="small">满足</StatusBadge> : null}
                      {hint === 'unknown' ? <StatusBadge tone="neutral" size="small">先填上限</StatusBadge> : null}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {isAuto && state?.current ? (
        <div className="auto-policy-panel__section" aria-label="auto policy running">
          <Typography.Text strong>运行中</Typography.Text>
          <Typography.Text type="tertiary">
            模型 {state.current.modelName} · 错误率上限 {pct(state.current.maxError)} · 抽检比例 {pct(state.current.sampleRate)}
          </Typography.Text>
          {state.samples ? (
            <Typography.Text type="tertiary">
              已自动准入 {state.samples.autoAccepted} 条 · 抽检 {state.samples.sampled} 条（已裁决 {state.samples.judged},打回 {state.samples.humanRejected}）·
              抽检错误率 95% 区间 {pct(state.samples.wilsonLower95)} – {pct(state.samples.wilsonUpper95)}（下界超过上限即熔断）
            </Typography.Text>
          ) : null}
          <Popconfirm title="关闭自动通过?" content="之后的提交恢复为人工初审;已自动准入的数据保持策略准入状态。" onConfirm={onDisable}>
            <Button loading={disabling} type="danger">关闭自动通过</Button>
          </Popconfirm>
        </div>
      ) : (
        <div className="auto-policy-panel__section auto-policy-panel__form" aria-label="auto policy enable form">
          <label className="ai-review-rule-field">
            <Typography.Text strong>门槛证据对应的模型</Typography.Text>
            <Select
              placeholder="选择模型"
              value={form.modelName || undefined}
              optionList={groups.map((g) => ({ label: g.modelName, value: g.modelName }))}
              onChange={(value) => setForm((f) => ({ ...f, modelName: String(value ?? '') }))}
            />
            {errors.modelName ? <Typography.Text className="ai-review-rule-error">{errors.modelName}</Typography.Text> : null}
          </label>
          <label className="ai-review-rule-field">
            <Typography.Text strong>自动通过错误率上限</Typography.Text>
            <Input inputMode="decimal" placeholder="必填,例如 0.05;由你承担问责,没有默认值" value={form.maxError}
              onChange={(value) => setForm((f) => ({ ...f, maxError: value }))} />
            {errors.maxError ? <Typography.Text className="ai-review-rule-error">{errors.maxError}</Typography.Text> : null}
          </label>
          <label className="ai-review-rule-field">
            <Typography.Text strong>抽检比例</Typography.Text>
            <Input inputMode="decimal" value={form.sampleRate} onChange={(value) => setForm((f) => ({ ...f, sampleRate: value }))} />
            {errors.sampleRate ? <Typography.Text className="ai-review-rule-error">{errors.sampleRate}</Typography.Text> : null}
            <Typography.Text type="tertiary">抽检样本照常送初审,并对初审员隐藏 AI 结论(盲审)。</Typography.Text>
          </label>
          {gateError ? <Typography.Text className="ai-review-rule-error" role="alert">{gateError}</Typography.Text> : null}
          <Button loading={enabling} theme="solid" type="warning" disabled={!rule.isCurrent || rule.status !== 'published'} onClick={submit}>
            开启自动通过
          </Button>
        </div>
      )}

      {state && state.events.length > 0 ? (
        <div className="auto-policy-panel__section" aria-label="auto policy history">
          <Typography.Text strong>历史（只追加）</Typography.Text>
          <ol className="auto-policy-panel__events">
            {state.events.map((event) => (
              <li key={event.id}>
                <StatusBadge tone={event.action === 'enable' ? 'warning' : event.action === 'circuit_break' ? 'danger' : 'neutral'} size="small">
                  {EVENT_LABELS[event.action]}
                </StatusBadge>
                <Typography.Text type="tertiary">
                  {event.createdAt.replace('T', ' ').replace(/(?:\.\d+)?Z$/, '')} · {event.actorType === 'system' ? '系统' : `Owner #${event.actorId}`}
                  {event.reason ? ` · ${event.reason}` : ''}
                </Typography.Text>
              </li>
            ))}
          </ol>
        </div>
      ) : null}
    </SectionCard>
  );
}

/** Container: binds the view to the task's current rule version. */
export function AutoPolicyPanel({ taskId }: { taskId: number }) {
  const rulesQuery = useListAiReviewRulesQuery(taskId);
  const rule = rulesQuery.data?.find((r) => r.isCurrent);
  const shadow = useShadowStatsQuery(rule?.id);
  const policy = useAutoPolicyQuery(rule?.id);
  const enable = useEnableAutoPolicyMutation(rule?.id ?? 0);
  const disable = useDisableAutoPolicyMutation(rule?.id ?? 0);
  const [gateError, setGateError] = useState('');

  if (!rule) return null;

  const onEnable = async (request: AiReviewAutoPolicyEnableRequest) => {
    setGateError('');
    try {
      await enable.mutateAsync(request);
      Toast.success('已开启自动通过');
    } catch (error) {
      setGateError(error instanceof AutoPolicyFailure ? error.userMessage : '开启自动通过失败');
    }
  };

  const onDisable = async () => {
    try {
      await disable.mutateAsync({ reason: 'owner disabled from rule panel' });
      Toast.success('已关闭自动通过');
    } catch (error) {
      Toast.error(error instanceof AutoPolicyFailure ? error.userMessage : '关闭自动通过失败');
    }
  };

  return (
    <AutoPolicyView
      rule={rule}
      shadowGroups={shadow.data?.groups ?? []}
      state={policy.data}
      enabling={enable.isPending}
      disabling={disable.isPending}
      gateError={gateError}
      onEnable={(request) => void onEnable(request)}
      onDisable={() => void onDisable()}
    />
  );
}
