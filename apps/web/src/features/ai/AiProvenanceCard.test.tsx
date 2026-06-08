import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AiCall } from '../../entities/ai/aiTypes';

const { provenanceQueryMock } = vi.hoisted(() => ({
  provenanceQueryMock: vi.fn(),
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconChevronDown: () => <span>down</span>,
  IconChevronUp: () => <span>up</span>,
  IconRefresh: () => <span>refresh</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children }: { children?: ReactNode }) => <button>{children}</button>,
  Card: ({ children, title }: { children?: ReactNode; title?: ReactNode }) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Tag: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Tooltip: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: { children?: ReactNode }) => <p>{children}</p>,
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
  },
}));

vi.mock('../../shared/ui/TruncatedHash', () => ({
  TruncatedHash: ({ value }: { value?: string | null }) => <span>{value}</span>,
}));

vi.mock('./useSubmissionAiProvenanceQuery', () => ({
  useSubmissionAiProvenanceQuery: provenanceQueryMock,
}));

import { AiProvenanceCard } from './AiProvenanceCard';

describe('AiProvenanceCard prompt evidence', () => {
  it('renders a Chinese owner-facing summary for modern AI calls', () => {
    provenanceQueryMock.mockReturnValueOnce(queryWithCall(makeAiCall({ promptVersionId: 7 })));

    const html = renderToString(<AiProvenanceCard submissionId={100} />);

    expect(html).toContain('AI 调用 #');
    expect(html).toContain('已完成');
    expect(html).toContain('提示词版本');
    expect(html).toContain('#<!-- -->7');
    expect(html).toContain('模型');
    expect(html).toContain('费用');
    expect(html).toContain('耗时');
    expect(html).toContain('完成时间');
    expect(html).toContain('技术指纹与适配器');
    expect(html).not.toContain('Prompt:');
    expect(html).not.toContain('Adapter:');
  });

  it('falls back to the prompt version label for legacy AI calls', () => {
    provenanceQueryMock.mockReturnValueOnce(queryWithCall(makeAiCall({ promptVersionId: null })));

    const html = renderToString(<AiProvenanceCard submissionId={100} />);

    expect(html).toContain('提示词版本');
    expect(html).toContain('promptVersion#7');
    expect(html).not.toContain('#null');
    expect(html).not.toContain('Adapter:');
  });

  it('collapses prompt evidence when the API exposes raw prompts', () => {
    provenanceQueryMock.mockReturnValueOnce(queryWithCall(makeAiCall({
      businessPrompt: 'Score the answer against the owner rubric.',
      renderedPrompt: 'Task 12 / Submission 100 / Rubric v7',
    })));

    const html = renderToString(<AiProvenanceCard submissionId={100} />);

    expect(html).toContain('提示词证据');
    expect(html).toContain('展开');
    expect(html).toContain('2 段提示词');
    expect(html).toContain('展开查看原始内容');
    expect(html).not.toContain('Business Prompt');
    expect(html).not.toContain('Rendered Prompt');
    expect(html).not.toContain('Score the answer against the owner rubric.');
    expect(html).not.toContain('Task 12 / Submission 100 / Rubric v7');
  });

  it('does not render raw prompt sections when the API withholds them', () => {
    provenanceQueryMock.mockReturnValueOnce(queryWithCall(makeAiCall()));

    const html = renderToString(<AiProvenanceCard submissionId={100} />);

    expect(html).not.toContain('提示词证据');
  });
});

function queryWithCall(call: AiCall) {
  return {
    data: {
      submissionId: 100,
      aiCalls: [call],
      fieldFindings: [],
    },
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  };
}

function makeAiCall(overrides: Partial<AiCall> = {}): AiCall {
  return {
    id: 7,
    submissionId: 100,
    purpose: 'submission_review',
    promptVersion: 'promptVersion#7',
    promptVersionId: 7,
    providerAdapterVersion: 'agent-default-v1',
    providerName: 'mock',
    modelName: 'mock-v1',
    inputHash: 'a'.repeat(64),
    outputHash: 'b'.repeat(64),
    status: 'completed',
    idempotencyKey: 'submission:100:provider:mock:model:mock-v1:promptVersionId:7:adapter:agent-default-v1',
    createdAt: '2026-05-28T00:00:00Z',
    completedAt: '2026-05-28T00:00:01Z',
    ...overrides,
  };
}
