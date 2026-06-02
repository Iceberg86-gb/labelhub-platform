import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

const reuseMocks = vi.hoisted(() => ({
  query: {
    data: {
      empty: true,
      generatedAt: '2026-06-02T12:00:00Z',
      idempotency: {
        callCount: 0,
        uniqueKeyCount: 0,
        duplicateKeyCount: 0,
        cacheHitTokens: 0,
      },
      unitCost: {
        totalCost: 0,
        distinctSubmissionCount: 0,
        distinctDatasetItemCount: 0,
        costPerSubmission: 0,
        costPerDatasetItem: 0,
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
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => <span className={className}>{children}</span>,
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => <h1 className={className}>{children}</h1>,
  },
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconRefresh: () => <span>refresh</span>,
}));

vi.mock('../../features/platform-metrics/usePlatformEfficiencyMetricsQuery', () => ({
  usePlatformEfficiencyMetricsQuery: () => reuseMocks.query,
}));

import { PlatformEfficiencyMetricsPage } from './PlatformEfficiencyMetricsPage';

describe('PlatformEfficiencyMetricsPage', () => {
  it('shows empty token reuse and unit cost facts without evaluative language', () => {
    const html = renderToString(<PlatformEfficiencyMetricsPage />);

    expect(html).toContain('Token 复用计量');
    expect(html).toContain('暂无记录');
    expect(html).toContain('cache hit token');
    expect(html).toContain('单位数据成本');
    const banned = [
      ['绩', '效'].join(''),
      ['效', '率', '评', '分'].join(''),
      ['排', '名'].join(''),
      [String.fromCharCode(0x6263)].join(''),
      ['拖', '后', '腿'].join(''),
      ['建', '议', '处', '理'].join(''),
    ];
    for (const phrase of banned) {
      expect(html).not.toContain(phrase);
    }
  });
});
