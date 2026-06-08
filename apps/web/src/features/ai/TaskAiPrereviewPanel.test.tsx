import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { TaskAiPrereviewSummary } from './useTaskAiPrereviewSummaryQuery';

const { summaryQueryMock, enqueueMutationMock } = vi.hoisted(() => ({
  summaryQueryMock: vi.fn(),
  enqueueMutationMock: vi.fn(),
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconBolt: () => <span>bolt</span>,
  IconRefresh: () => <span>refresh</span>,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, disabled }: { children?: ReactNode; disabled?: boolean }) => (
    <button disabled={disabled}>{children}</button>
  ),
  Card: ({ children }: { children?: ReactNode }) => <section>{children}</section>,
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Spin: () => <span>loading</span>,
  Toast: {
    error: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
  },
  Typography: {
    Text: ({ children }: { children?: ReactNode }) => <span>{children}</span>,
    Title: ({ children }: { children?: ReactNode }) => <h2>{children}</h2>,
  },
}));

vi.mock('./useTaskAiPrereviewSummaryQuery', () => ({
  useTaskAiPrereviewSummaryQuery: summaryQueryMock,
}));

vi.mock('./useEnqueueTaskAiPrereviewsMutation', () => ({
  EnqueueTaskAiPrereviewsFailure: class EnqueueTaskAiPrereviewsFailure extends Error {},
  useEnqueueTaskAiPrereviewsMutation: enqueueMutationMock,
}));

import { TaskAiPrereviewPanel } from './TaskAiPrereviewPanel';

describe('TaskAiPrereviewPanel', () => {
  it('shows a queued state instead of unavailable when submissions are waiting for the agent', () => {
    summaryQueryMock.mockReturnValueOnce(queryWithSummary({
      taskId: 4,
      totalCount: 1,
      pendingCount: 1,
      processingCount: 0,
      completedCount: 0,
      failedCount: 0,
      enqueueableCount: 0,
    }));
    enqueueMutationMock.mockReturnValueOnce({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<TaskAiPrereviewPanel taskId={4} />);

    expect(html).toContain('等待 Agent');
    expect(html).toContain('已排队');
    expect(html).not.toContain('暂无可发起');
  });

  it('keeps the empty action when the task has no submitted records', () => {
    summaryQueryMock.mockReturnValueOnce(queryWithSummary({
      taskId: 4,
      totalCount: 0,
      pendingCount: 0,
      processingCount: 0,
      completedCount: 0,
      failedCount: 0,
      enqueueableCount: 0,
    }));
    enqueueMutationMock.mockReturnValueOnce({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<TaskAiPrereviewPanel taskId={4} />);

    expect(html).toContain('暂无可发起');
    expect(html).not.toContain('等待 Agent');
  });
});

function queryWithSummary(summary: TaskAiPrereviewSummary) {
  return {
    data: summary,
    isError: false,
    isFetching: false,
    isLoading: false,
    refetch: vi.fn(),
  };
}
