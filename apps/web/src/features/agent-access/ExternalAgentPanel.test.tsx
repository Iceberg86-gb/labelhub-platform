import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AgentRunSummary, AgentTrajectoryStep, McpToken } from './agentAccessTypes';

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children }: { children?: ReactNode }) => <button>{children}</button>,
  Input: ({ placeholder }: { placeholder?: string }) => <input placeholder={placeholder} readOnly />,
  InputNumber: () => <input readOnly />,
  Popconfirm: ({ children }: { children?: ReactNode }) => <>{children}</>,
  Spin: () => <span>loading</span>,
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => <div>{title}{description}</div>,
  Toast: { success: vi.fn(), error: vi.fn() },
  Tooltip: ({ children }: { children?: ReactNode }) => <>{children}</>,
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

const { IssuedTokenNotice, RunTable, TokenTable, TrajectoryView, claudeCodeCommand, validateTokenForm } = await import('./ExternalAgentPanel');

const token = (over: Partial<McpToken> = {}): McpToken => ({
  id: 1, taskId: 44, name: 'claude-code-alice', tokenPrefix: 'lhm_AbCdEfGh', createdAt: '2026-11-07T10:00:00Z',
  expiresAt: '2026-12-07T10:00:00Z', status: 'active', ...over,
});

describe('external agent panel (ADR-022)', () => {
  it('validates the token form', () => {
    expect(validateTokenForm('', 30)).toContain('名称');
    expect(validateTokenForm('x'.repeat(81), 30)).toContain('80');
    expect(validateTokenForm('agent', 91)).toContain('1–90');
    expect(validateTokenForm('agent', undefined)).toBeNull();
    expect(validateTokenForm('agent', 30)).toBeNull();
  });

  it('shows the plaintext once with a ready-to-paste Claude Code command', () => {
    const html = renderToString(<IssuedTokenNotice issued={{ token: token(), plaintext: 'lhm_secret' }} url="http://localhost:8081/mcp" />);
    expect(html).toContain('只显示这一次');
    expect(html).toContain('lhm_secret');
    expect(claudeCodeCommand('http://h/mcp', 'lhm_x')).toBe('claude mcp add --transport http labelhub http://h/mcp --header "Authorization: Bearer lhm_x"');
  });

  it('lists tokens by prefix only and offers revoke for active ones', () => {
    const html = renderToString(<TokenTable tokens={[token(), token({ id: 2, status: 'revoked', revokedAt: '2026-11-08T00:00:00Z' })]} onRevoke={() => {}} />);
    expect(html).toContain('lhm_AbCdEfGh…');
    expect(html).toContain('有效');
    expect(html).toContain('已撤销');
    expect(html.match(/撤销<\/button>/g)).toHaveLength(1);
    expect(renderToString(<TokenTable tokens={[]} onRevoke={() => {}} />)).toContain('尚未签发令牌');
  });

  it('summarizes runs and renders the trajectory with policy decisions and hashes', () => {
    const run: AgentRunSummary = { runId: 'r1', tokenId: 1, tokenName: 'alice', clientName: 'claude-code', clientVersion: '2.1', steps: 3,
      proposals: 1, startedAt: '2026-11-07T10:00:00Z', lastStepAt: '2026-11-07T10:01:00Z', lastStatus: 'ok' };
    expect(renderToString(<RunTable runs={[run]} onSelect={() => {}} />)).toContain('claude-code 2.1');
    const steps: AgentTrajectoryStep[] = [
      { id: 1, runId: 'r1', stepNo: 1, taskId: 44, tokenId: 1, clientName: 'claude-code', toolName: 'submit_review', inputHash: 'a'.repeat(64),
        outputHash: 'b'.repeat(64), inputSummary: '{"submissionId":300}', status: 'error', policyDecision: 'proposal_rejected_invalid',
        submissionId: 300, latencyMs: 12, createdAt: '2026-11-07T10:00:00Z' },
      { id: 2, runId: 'r1', stepNo: 2, taskId: 44, tokenId: 1, clientName: 'claude-code', toolName: 'submit_review', inputHash: 'c'.repeat(64),
        status: 'ok', policyDecision: 'proposal_recorded', aiCallId: 9, submissionId: 300, latencyMs: 30, createdAt: '2026-11-07T10:00:05Z' },
    ];
    const html = renderToString(<TrajectoryView steps={steps} />);
    expect(html).toContain('#1 submit_review');
    expect(html).toContain('提议未通过校验，已拒绝');
    expect(html).toContain('提议已记录为 AI 证据');
    expect(html).toContain('AI 调用 #9');
    expect(html).toContain('{&quot;submissionId&quot;:300}');
  });
});
