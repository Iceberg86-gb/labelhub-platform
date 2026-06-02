import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

const costMocks = vi.hoisted(() => ({
  query: {
    data: {
      empty: true,
      generatedAt: '2026-06-02T12:00:00Z',
      overview: {
        callCount: 0,
        totalTokens: 0,
        totalCost: 0,
        attributedCallCount: 0,
        attributedTokens: 0,
        attributedCost: 0,
        unattributedCallCount: 0,
        unattributedTokens: 0,
        unattributedCost: 0,
      },
      dailyTrend: [],
      modelBreakdown: [],
      taskBreakdown: [],
      ownerBreakdown: [],
    },
    isError: false,
    isFetching: false,
    isLoading: false,
    refetch: vi.fn(),
  },
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children }: { children?: ReactNode }) => <button type="button">{children}</button>,
  Empty: ({ description, title }: { description?: ReactNode; title?: ReactNode }) => <div>{title}{description}</div>,
  Spin: () => <span>loading</span>,
  Table: ({ columns, dataSource }: { columns?: Array<any>; dataSource?: Array<Record<string, any>> }) => (
    <table>
      <thead>
        <tr>{columns?.map((column, index) => <th key={column.dataIndex ?? index}>{column.title}</th>)}</tr>
      </thead>
      <tbody>
        {dataSource?.map((row, rowIndex) => (
          <tr key={rowIndex}>
            {columns?.map((column, index) => (
              <td key={column.dataIndex ?? index}>{column.render ? column.render(row[column.dataIndex], row) : String(row[column.dataIndex] ?? '')}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => <span className={className}>{children}</span>,
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => <h1 className={className}>{children}</h1>,
  },
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconRefresh: () => <span>refresh</span>,
}));

vi.mock('../../features/platform-cost/usePlatformCostMetricsQuery', () => ({
  usePlatformCostMetricsQuery: () => costMocks.query,
}));

import { PlatformCostDashboardPage } from './PlatformCostDashboardPage';

describe('PlatformCostDashboardPage', () => {
  it('shows an explicit empty state without evaluative language', () => {
    const html = renderToString(<PlatformCostDashboardPage />);

    expect(html).toContain('Token 成本管控');
    expect(html).toContain('暂无 AI 调用记录');
    expect(html).toContain('未归集');
    expect(html).toContain('总 token');
    const banned = [
      ['绩', '效'].join(''),
      ['效', '率', '评', '分'].join(''),
      ['扣', '钱'].join(''),
      ['\u6392\u540d', '\u597d\u574f'].join(''),
    ];
    for (const phrase of banned) {
      expect(html).not.toContain(phrase);
    }
  });
});
