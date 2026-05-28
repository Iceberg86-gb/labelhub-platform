import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AiCall } from '../../entities/ai/aiTypes';

const { provenanceQueryMock } = vi.hoisted(() => ({
  provenanceQueryMock: vi.fn(),
}));

vi.mock('@douyinfe/semi-icons', () => ({
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
  it('renders prompt version id and provider adapter for modern AI calls', () => {
    provenanceQueryMock.mockReturnValueOnce(queryWithCall(makeAiCall({ promptVersionId: 7 })));

    const html = renderToString(<AiProvenanceCard submissionId={100} />);

    expect(html).toContain('Prompt: <!-- -->promptVersion#7');
    expect(html).toContain('Prompt ID:');
    expect(html).toContain('#<!-- -->7');
    expect(html).toContain('Adapter: <!-- -->agent-default-v1');
  });

  it('omits the prompt id row for legacy AI calls', () => {
    provenanceQueryMock.mockReturnValueOnce(queryWithCall(makeAiCall({ promptVersionId: null })));

    const html = renderToString(<AiProvenanceCard submissionId={100} />);

    expect(html).toContain('Prompt: <!-- -->promptVersion#7');
    expect(html).not.toContain('Prompt ID:');
    expect(html).not.toContain('#null');
    expect(html).toContain('Adapter: <!-- -->agent-default-v1');
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
