import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { AiCall, AiReviewResult } from '../../entities/ai/aiTypes';

vi.mock('@douyinfe/semi-ui', () => ({
  Banner: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  SideSheet: ({ children, title }: { children?: ReactNode; title?: ReactNode }) => (
    <section>
      <h2>{title}</h2>
      {children}
    </section>
  ),
  Spin: () => <span>loading</span>,
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

import { AiReviewDrawer } from './AiReviewDrawer';

describe('AiReviewDrawer prompt evidence', () => {
  it('renders prompt version id and provider adapter for modern AI calls', () => {
    const html = renderToString(
      <AiReviewDrawer open loading={false} result={makeResult(makeAiCall({ promptVersionId: 7 }))} onClose={() => {}} />,
    );

    expect(html).toContain('Prompt');
    expect(html).toContain('promptVersion#7');
    expect(html).toContain('Prompt ID');
    expect(html).toContain('#7');
    expect(html).toContain('Adapter');
    expect(html).toContain('agent-default-v1');
  });

  it('omits the prompt id row for legacy AI calls', () => {
    const html = renderToString(
      <AiReviewDrawer open loading={false} result={makeResult(makeAiCall({ promptVersionId: null }))} onClose={() => {}} />,
    );

    expect(html).toContain('promptVersion#7');
    expect(html).not.toContain('Prompt ID');
    expect(html).not.toContain('#null');
    expect(html).toContain('Adapter');
    expect(html).toContain('agent-default-v1');
  });
});

function makeResult(aiCall: AiCall): AiReviewResult {
  return {
    aiCall,
    fieldFindings: [],
    overallSuggestion: 'looks_good',
    summary: 'ok',
    idempotencyHit: false,
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
