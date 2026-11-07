import { Button, Input, InputNumber, Popconfirm, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import { useState } from 'react';
import { EmptyState, SectionCard, StatusBadge, TruncatedHash, type BadgeTone } from '../../shared/ui';
import type { AgentRunSummary, AgentTrajectoryStep, McpToken, McpTokenIssued } from './agentAccessTypes';
import {
  useAgentRunStepsQuery,
  useAgentRunsQuery,
  useIssueMcpTokenMutation,
  useMcpTokensQuery,
  useRevokeMcpTokenMutation,
} from './useAgentAccess';

/** Where external agents connect (ADR-022 §5). Override with VITE_MCP_URL behind a reverse proxy. */
export const mcpUrl: string = import.meta.env.VITE_MCP_URL ?? 'http://localhost:8081/mcp';

export function claudeCodeCommand(url: string, token: string): string {
  return `claude mcp add --transport http labelhub ${url} --header "Authorization: Bearer ${token}"`;
}

const tokenStatus: Record<McpToken['status'], { label: string; tone: BadgeTone }> = {
  active: { label: '有效', tone: 'success' },
  revoked: { label: '已撤销', tone: 'neutral' },
  expired: { label: '已过期', tone: 'warning' },
};

const stepStatus: Record<AgentTrajectoryStep['status'], { label: string; tone: BadgeTone }> = {
  ok: { label: '成功', tone: 'success' },
  error: { label: '失败', tone: 'danger' },
  denied: { label: '拒绝', tone: 'warning' },
};

export const policyDecisionLabels: Record<string, string> = {
  proposal_recorded: '提议已记录为 AI 证据',
  proposal_replayed: '已有 AI 审核（重放，未新增）',
  proposal_rejected_invalid: '提议未通过校验，已拒绝',
  handoff_to_human: '交还人工',
  denied_scope: '越权访问被拒绝',
  token_rejected: '令牌已撤销或过期，调用被拒绝',
};

function when(value?: string | null) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
    .format(new Date(value));
}

export function validateTokenForm(name: string, days: number | undefined): string | null {
  if (!name.trim()) return '请填写令牌名称，例如使用它的 agent';
  if (name.trim().length > 80) return '名称最多 80 个字符';
  if (days !== undefined && (!Number.isInteger(days) || days < 1 || days > 90)) return '有效期为 1–90 天';
  return null;
}

export function IssuedTokenNotice({ issued, url }: { issued: McpTokenIssued; url: string }) {
  return (
    <div className="external-agent-panel__issued" role="status">
      <Typography.Text strong>令牌只显示这一次，请立即复制保存。</Typography.Text>
      <code className="external-agent-panel__code">{issued.plaintext}</code>
      <Typography.Text type="tertiary">在 Claude Code 中接入：</Typography.Text>
      <code className="external-agent-panel__code">{claudeCodeCommand(url, issued.plaintext)}</code>
    </div>
  );
}

export function TokenTable({ tokens, onRevoke, revokingId }: { tokens: McpToken[]; onRevoke: (id: number) => void; revokingId?: number }) {
  if (tokens.length === 0) {
    return <EmptyState variant="inline" title="尚未签发令牌" description="令牌只对本任务有效；外部 agent 的每次调用都会记录到轨迹。" />;
  }
  return (
    <table className="external-agent-panel__table">
      <thead>
        <tr><th>名称</th><th>前缀</th><th>状态</th><th>签发</th><th>到期</th><th /></tr>
      </thead>
      <tbody>
        {tokens.map((t) => (
          <tr key={t.id}>
            <td>{t.name}</td>
            <td><code>{`${t.tokenPrefix}…`}</code></td>
            <td><StatusBadge tone={tokenStatus[t.status].tone}>{tokenStatus[t.status].label}</StatusBadge></td>
            <td>{when(t.createdAt)}</td>
            <td>{when(t.expiresAt)}</td>
            <td>
              {t.status === 'active' ? (
                <Popconfirm title="撤销后，使用该令牌的下一次调用即被拒绝" onConfirm={() => onRevoke(t.id)}>
                  <Button size="small" type="danger" loading={revokingId === t.id}>撤销</Button>
                </Popconfirm>
              ) : null}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function RunTable({ runs, selected, onSelect }: { runs: AgentRunSummary[]; selected?: string; onSelect: (runId: string) => void }) {
  if (runs.length === 0) {
    return <EmptyState variant="inline" title="暂无外部 agent 运行" description="外部 agent 连接 MCP 并调用工具后，运行记录会出现在这里。" />;
  }
  return (
    <table className="external-agent-panel__table">
      <thead>
        <tr><th>客户端</th><th>令牌</th><th>步数</th><th>记录的提议</th><th>最后状态</th><th>最后一步</th><th /></tr>
      </thead>
      <tbody>
        {runs.map((r) => (
          <tr key={r.runId} className={r.runId === selected ? 'external-agent-panel__row--selected' : undefined}>
            <td>{r.clientVersion ? `${r.clientName} ${r.clientVersion}` : r.clientName}</td>
            <td>{r.tokenName}</td>
            <td>{r.steps}</td>
            <td>{r.proposals}</td>
            <td><StatusBadge tone={stepStatus[r.lastStatus].tone}>{stepStatus[r.lastStatus].label}</StatusBadge></td>
            <td>{when(r.lastStepAt)}</td>
            <td><Button size="small" theme="borderless" onClick={() => onSelect(r.runId)}>查看轨迹</Button></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function TrajectoryView({ steps }: { steps: AgentTrajectoryStep[] }) {
  if (steps.length === 0) {
    return <EmptyState variant="inline" title="该运行没有步骤" />;
  }
  return (
    <ol className="external-agent-panel__steps" aria-label="Agent trajectory">
      {steps.map((s) => (
        <li key={s.id} className="external-agent-panel__step">
          <div className="external-agent-panel__step-head">
            <Typography.Text strong>{`#${s.stepNo} ${s.toolName}`}</Typography.Text>
            <StatusBadge tone={stepStatus[s.status].tone}>{stepStatus[s.status].label}</StatusBadge>
            {s.policyDecision ? <StatusBadge tone="info">{policyDecisionLabels[s.policyDecision] ?? s.policyDecision}</StatusBadge> : null}
            {s.submissionId ? <Typography.Text type="tertiary">{`提交 #${s.submissionId}`}</Typography.Text> : null}
            {s.aiCallId ? <Typography.Text type="tertiary">{`AI 调用 #${s.aiCallId}`}</Typography.Text> : null}
            <Typography.Text type="tertiary">{s.latencyMs} ms · {when(s.createdAt)}</Typography.Text>
          </div>
          <div className="external-agent-panel__io">
            <span>输入 <TruncatedHash value={s.inputHash} /></span>
            {s.inputSummary ? <code className="external-agent-panel__summary">{s.inputSummary}</code> : null}
            <span>输出 <TruncatedHash value={s.outputHash} /></span>
            {s.outputSummary ? <code className="external-agent-panel__summary">{s.outputSummary}</code> : null}
          </div>
        </li>
      ))}
    </ol>
  );
}

export function ExternalAgentPanel({ taskId }: { taskId: number }) {
  const tokensQuery = useMcpTokensQuery(taskId);
  const runsQuery = useAgentRunsQuery(taskId);
  const issue = useIssueMcpTokenMutation(taskId);
  const revoke = useRevokeMcpTokenMutation(taskId);
  const [name, setName] = useState('');
  const [days, setDays] = useState<number | undefined>(30);
  const [issued, setIssued] = useState<McpTokenIssued | null>(null);
  const [runId, setRunId] = useState<string | undefined>();
  const stepsQuery = useAgentRunStepsQuery(runId);
  const formError = validateTokenForm(name, days);

  const submit = () => {
    if (formError) return;
    issue.mutate({ name: name.trim(), expiresInDays: days }, {
      onSuccess: (data) => { setIssued(data); setName(''); },
      onError: (error) => Toast.error(error.message),
    });
  };

  return (
    <SectionCard
      className="external-agent-panel"
      title="外部 Agent（MCP）"
      subtitle="外部 agent 只能提交审核提议：提议经过同样的校验和评分，记录为 AI 证据，永远不会自动通过，最终由人工裁决。"
    >
      <div className="external-agent-panel__section">
        <Typography.Text strong>访问令牌</Typography.Text>
        <div className="external-agent-panel__form">
          <Input placeholder="令牌名称，例如 claude-code-alice" value={name} onChange={setName} maxLength={80} />
          <InputNumber min={1} max={90} value={days} onChange={(v) => setDays(typeof v === 'number' ? v : undefined)} suffix="天" />
          <Button theme="solid" disabled={Boolean(formError)} loading={issue.isPending} onClick={submit}>签发令牌</Button>
        </div>
        {name && formError ? <Typography.Text type="danger">{formError}</Typography.Text> : null}
        {issued ? <IssuedTokenNotice issued={issued} url={mcpUrl} /> : null}
        {tokensQuery.isLoading ? <Spin /> : (
          <TokenTable
            tokens={tokensQuery.data ?? []}
            revokingId={revoke.isPending ? revoke.variables : undefined}
            onRevoke={(id) => revoke.mutate(id, { onError: (error) => Toast.error(error.message) })}
          />
        )}
      </div>
      <div className="external-agent-panel__section">
        <Typography.Text strong>运行与轨迹</Typography.Text>
        {runsQuery.isLoading ? <Spin /> : <RunTable runs={runsQuery.data ?? []} selected={runId} onSelect={setRunId} />}
        {runId ? (stepsQuery.isLoading ? <Spin /> : <TrajectoryView steps={stepsQuery.data ?? []} />) : null}
      </div>
    </SectionCard>
  );
}
