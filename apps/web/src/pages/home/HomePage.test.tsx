import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@douyinfe/semi-ui', () => ({
  Spin: () => <span>loading</span>,
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h1 className={className}>{children}</h1>
    ),
  },
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, className, to }: { children?: ReactNode; className?: string; to: string }) => (
    <a className={className} href={to}>{children}</a>
  ),
}));

vi.mock('../../shared/api/auth-storage', () => ({
  getUser: () => ({
    displayName: 'Multi Role Demo',
    roles: ['OWNER', 'LABELER'],
  }),
}));

vi.mock('../../features/task/list-tasks/useTasksQuery', () => ({
  useTasksQuery: ({ status }: { status?: string }) => ({
    data: status
      ? { total: status === 'published' ? 2 : 1, items: [], page: 1, size: 1 }
      : {
          total: 3,
          page: 1,
          size: 5,
          items: [{ id: 11, title: 'Demo task', status: 'published', quotaClaimed: 4, quotaTotal: 12 }],
        },
    fetchStatus: 'idle',
    isError: false,
    isLoading: false,
  }),
}));

vi.mock('../../features/schema-design/useSchemasQuery', () => ({
  useSchemasQuery: () => ({
    data: { total: 1, page: 1, size: 5, items: [] },
    fetchStatus: 'idle',
    isError: false,
    isLoading: false,
  }),
}));

vi.mock('../../features/labeling/useMarketplaceQuery', () => ({
  useMarketplaceQuery: () => ({
    data: {
      total: 2,
      page: 1,
      size: 5,
      items: [{ id: 21, title: 'Marketplace task', availableItemCount: 7, quotaClaimed: 3, quotaTotal: 10 }],
    },
    fetchStatus: 'idle',
    isError: false,
    isLoading: false,
  }),
}));

vi.mock('../../features/labeling/useMySessionsQuery', () => ({
  useMySessionsQuery: ({ workStatus }: { workStatus?: string }) => ({
    data: workStatus
      ? { total: workStatus === 'in_progress' ? 1 : 0, page: 1, size: 1, items: [], summary: {} }
      : {
          total: 2,
          page: 1,
          size: 5,
          summary: {},
          items: [{ id: 31, taskId: 21, workStatus: 'in_progress' }],
        },
    fetchStatus: 'idle',
    isError: false,
    isLoading: false,
  }),
}));

vi.mock('../../features/quality/useReviewerQueueQuery', () => ({
  useReviewerQueueQuery: () => ({
    data: { total: 0, page: 1, size: 5, items: [] },
    fetchStatus: 'idle',
    isError: false,
    isLoading: false,
  }),
}));

import { HomePage } from './HomePage';

describe('HomePage', () => {
  it('renders a real data dashboard and union role entry cards', () => {
    const html = renderToString(<HomePage />);

    expect(html).toContain('home-page');
    expect(html).toContain('home-dashboard');
    expect(html).not.toContain('home-product-preview');
    expect(html).toContain('实时数据看板');
    expect(html).toContain('任务总数');
    expect(html).toContain('Demo task');
    expect(html).toContain('Marketplace task');
    expect(html).toContain('Session #31');
    expect(html).toContain('OWNER');
    expect(html).toContain('LABELER');
    expect(html).toContain('任务管理');
    expect(html).toContain('Designer 画布');
    expect(html).toContain('LLM 接入');
    expect(html).toContain('配置 API Key、模型和辅助范围');
    expect(html).toContain('标注工作台');
    expect(html).toContain('任务广场');
    expect(html).not.toContain('审核队列');
    expect(html).not.toContain('复核队列');
  });
});
