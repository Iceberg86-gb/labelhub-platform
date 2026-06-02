import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

const laborMocks = vi.hoisted(() => ({
  query: {
    data: {
      empty: true,
      generatedAt: '2026-06-02T12:00:00Z',
      submissions: [],
      reviews: [],
      rework: {
        supersededSubmissionCount: 0,
        multiRoundReviewActionCount: 0,
        returnedForRevisionSubmissionCount: 0,
      },
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

vi.mock('../../features/platform-metrics/usePlatformLaborMetricsQuery', () => ({
  usePlatformLaborMetricsQuery: () => laborMocks.query,
}));

import { PlatformLaborMetricsPage } from './PlatformLaborMetricsPage';

describe('PlatformLaborMetricsPage', () => {
  it('shows empty labor facts and separate rework definitions without evaluative language', () => {
    const html = renderToString(<PlatformLaborMetricsPage />);

    expect(html).toContain('人力计量');
    expect(html).toContain('暂无记录');
    expect(html).toContain('被取代');
    expect(html).toContain('多轮审核');
    expect(html).toContain('被打回');
    const banned = [
      ['绩', '效'].join(''),
      ['效', '率', '评', '分'].join(''),
      ['排', '名'].join(''),
      ['拖', '后', '腿'].join(''),
      ['建', '议', '处', '理'].join(''),
    ];
    for (const phrase of banned) {
      expect(html).not.toContain(phrase);
    }
  });
});
