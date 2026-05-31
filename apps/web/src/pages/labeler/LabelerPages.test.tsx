import type { ReactElement, ReactNode } from 'react';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { afterEach, describe, expect, it, vi } from 'vitest';

const marketplaceQueryMock = vi.hoisted(() => vi.fn());
const mySessionsQueryMock = vi.hoisted(() => vi.fn());
const sessionDetailQueryMock = vi.hoisted(() => vi.fn());
const latestDraftQueryMock = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({
  navigate: vi.fn(),
  params: { sessionId: '11' },
  searchParams: new URLSearchParams(),
  setSearchParams: vi.fn(),
}));

function textFrom(value: ReactNode) {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
}

vi.mock('@douyinfe/semi-icons', () => ({
  IconChevronLeft: () => <span />,
  IconChevronRight: () => <span />,
  IconPlay: () => <span />,
  IconRefresh: () => <span />,
  IconSearch: () => <span />,
  IconSend: () => <span />,
}));

function MockSelect({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={className}>{children}</div>;
}

MockSelect.Option = ({ children }: { children?: ReactNode }) => <span>{children}</span>;

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className, icon }: { children?: ReactNode; className?: string; icon?: ReactNode }) => (
    <button className={className}>
      {icon}
      {children}
    </button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <section className={className}>{children}</section>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Input: ({ value, placeholder }: { value?: string; placeholder?: string }) => (
    <input placeholder={placeholder} value={value} readOnly />
  ),
  Select: MockSelect,
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Spin: () => <div />,
  Table: ({ columns, dataSource }: { columns: Array<any>; dataSource: Array<any> }) => (
    <table>
      <tbody>
        {dataSource.map((record) => (
          <tr key={record.id}>
            {columns.map((column, index) => (
              <td key={column.dataIndex ?? index}>
                {column.render ? column.render(record[column.dataIndex], record) : textFrom(record[column.dataIndex])}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
  Typography: {
    Paragraph: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <p className={className}>{children}</p>
    ),
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h2 className={className}>{children}</h2>
    ),
  },
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => routeState.navigate,
  useParams: () => routeState.params,
  useSearchParams: () => [routeState.searchParams, routeState.setSearchParams],
}));

vi.mock('../../features/labeling/useMarketplaceQuery', () => ({
  useMarketplaceQuery: marketplaceQueryMock,
}));

vi.mock('../../features/labeling/useClaimMutation', () => ({
  ClaimTaskFailure: class ClaimTaskFailure extends Error {},
  useClaimMutation: () => ({ isPending: false, mutateAsync: vi.fn() }),
}));

vi.mock('../../features/labeling/useMySessionsQuery', () => ({
  useMySessionsQuery: mySessionsQueryMock,
}));

vi.mock('../../features/labeling/useSessionDetailQuery', () => ({
  useSessionDetailQuery: sessionDetailQueryMock,
}));

vi.mock('../../features/labeling/useLatestDraftQuery', () => ({
  useLatestDraftQuery: latestDraftQueryMock,
}));

vi.mock('../../features/labeling/useSaveDraftMutation', () => ({
  useSaveDraftMutation: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock('../../features/labeling/useSubmitMutation', () => ({
  SubmitValidationError: class SubmitValidationError extends Error {
    fieldErrors = [];
  },
  useSubmitMutation: () => ({ isPending: false, mutateAsync: vi.fn() }),
}));

vi.mock('../../features/labeling/useAutosave', () => ({
  useAutosave: () => ({
    disable: vi.fn(),
    flush: vi.fn(),
    isDirty: false,
    isSaving: false,
    lastSavedAt: null,
    status: 'idle',
  }),
}));

vi.mock('../../features/labeling/useOfflineDraftBuffer', () => ({
  applyOfflineDraftHydrationResult: (result: { payload: Record<string, unknown> }, guard: { isCancelled: () => boolean }, apply: (payload: Record<string, unknown>) => void) => {
    if (guard.isCancelled()) return false;
    apply(result.payload);
    return true;
  },
  createOfflineDraftHydrationGuard: () => ({
    cancel: vi.fn(),
    isCancelled: () => false,
  }),
  useOfflineDraftBuffer: () => ({
    bufferPending: vi.fn(),
    hydrate: vi.fn(async ({ serverPayload }: { serverPayload: Record<string, unknown> }) => ({
      payload: serverPayload,
      source: 'server',
    })),
    status: { kind: 'idle' },
  }),
}));

vi.mock('../../features/labeling/useOfflineDraftSync', () => ({
  useOfflineDraftSync: () => ({
    discardPending: vi.fn(),
    retryPending: vi.fn(),
    status: { kind: 'idle' },
    syncPendingForSubmit: vi.fn(),
  }),
}));

vi.mock('../../features/labeling/AutosaveStatusTag', () => ({
  AutosaveStatusTag: () => <span>autosave</span>,
}));

vi.mock('../../features/labeling/DatasetItemContextCard', () => ({
  DatasetItemContextCard: () => <section className="dataset-item-context-card">题目原文</section>,
  selectDatasetItemPayload: ({ datasetItemPayload }: { datasetItemPayload: Record<string, unknown> }) => ({
    payload: datasetItemPayload,
    source: 'dataset-item',
  }),
}));

vi.mock('../../features/labeling/formily/SchemaFormilyRenderer', () => ({
  SchemaFormilyRenderer: () => <section>作答表单</section>,
}));

vi.mock('../../features/labeling/SubmitConfirmModal', () => ({
  SubmitConfirmModal: () => null,
}));

vi.mock('../../shared/api/auth-storage', () => ({
  getUser: () => ({ id: 1002, roles: ['LABELER'] }),
}));

import { LabelerMarketplacePage } from './LabelerMarketplacePage';
import { LabelerMySessionsPage } from './LabelerMySessionsPage';
import { LabelerSessionPage } from './LabelerSessionPage';

const marketplaceTask = {
  availableItemCount: 8,
  deadlineAt: '2026-06-01T12:00:00Z',
  description: '判断客服回复是否合规。',
  id: 22,
  quotaClaimed: 4,
  quotaTotal: 20,
  tags: ['客服', '风控'],
  title: '客服回复质检',
};

const session = {
  claimedAt: '2026-05-30T08:00:00Z',
  claimSnapshot: { datasetItemOrdinal: 1 },
  datasetItemId: 301,
  id: 11,
  schemaVersionId: 77,
  status: 'claimed',
  submittedAt: undefined,
  taskId: 22,
  workStatus: 'in_progress',
};

const sessionDetail = {
  datasetItem: { itemPayload: { prompt: '请判断这段回复是否合规。' } },
  latestDraft: { payload: { answer: '合规' } },
  previousReviewFeedback: null,
  schemaVersion: {
    id: 77,
    schemaJson: {
      fields: [
        {
          label: '结论',
          stableId: 'answer',
          type: 'text',
        },
      ],
    },
    versionNumber: 1,
  },
  session,
  task: {
    description: '按题目原文完成标注。',
    id: 22,
    title: '客服回复质检',
  },
};

afterEach(() => {
  marketplaceQueryMock.mockReset();
  mySessionsQueryMock.mockReset();
  sessionDetailQueryMock.mockReset();
  latestDraftQueryMock.mockReset();
  routeState.navigate.mockReset();
  routeState.setSearchParams.mockReset();
  routeState.params = { sessionId: '11' };
  routeState.searchParams = new URLSearchParams();
});

describe('Labeler pages design shell', () => {
  it('renders marketplace as a labeler workbench with filter and claim cards', () => {
    marketplaceQueryMock.mockReturnValue({
      data: { items: [marketplaceTask], total: 1 },
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });

    const html = renderToString(<LabelerMarketplacePage />);

    expect(html).toContain('labeler-page labeler-page--marketplace');
    expect(html).toContain('labeler-workbench-hero');
    expect(html).toContain('LABELER');
    expect(html).toContain('marketplace-filter-bar labeler-workbench-filter-bar');
    expect(html).toContain('task-table-surface task-table-surface--labeler');
    expect(html).toContain('marketplace-task-card marketplace-task-card--claimable');
    expect(html).toContain('客服回复质检');
  });

  it('renders my sessions as a quiet labeler data desk', () => {
    mySessionsQueryMock.mockReturnValue({
      data: {
        items: [session],
        summary: { approved: 0, rejected: 0, returnedForRevision: 0, submitted: 1 },
        total: 1,
      },
      isError: false,
      isLoading: false,
    });

    const html = renderToString(<LabelerMySessionsPage />);

    expect(html).toContain('labeler-page labeler-page--sessions');
    expect(html).toContain('labeler-workbench-hero');
    expect(html).toContain('labeler-session-summary-grid labeler-session-summary-grid--quiet');
    expect(html).toContain('task-table-surface task-table-surface--labeler labeler-workbench-table');
    expect(html).toContain('LABELER');
    expect(html).toContain('审核中');
  });

  it('renders the answer workspace with context rail and answer panel', async () => {
    sessionDetailQueryMock.mockReturnValue({
      data: sessionDetail,
      isError: false,
      isLoading: false,
      isSuccess: true,
    });
    latestDraftQueryMock.mockReturnValue({
      data: { payload: { answer: '合规' } },
      isLoading: false,
    });
    mySessionsQueryMock.mockImplementation(({ status }: { status?: string }) => ({
      data: { items: status === 'claimed' ? [session] : [], total: status === 'claimed' ? 1 : 0 },
      isError: false,
      isLoading: false,
    }));

    const rendered = await renderClient(<LabelerSessionPage />);

    expect(rendered.html()).toContain('labeler-session-page labeler-session-page--workspace');
    expect(rendered.html()).toContain('labeler-session-header labeler-session-hero');
    expect(rendered.html()).toContain('labeler-session-navigation labeler-session-navigation--compact');
    expect(rendered.html()).toContain('labeler-session-layout');
    expect(rendered.html()).toContain('labeler-context-rail');
    expect(rendered.html()).toContain('labeler-answer-panel');
    expect(rendered.html()).toContain('labeler-session-card labeler-session-card--answer');

    rendered.unmount();
  });
});

async function renderClient(element: ReactElement) {
  const actEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
  };
  const previousActEnvironment = actEnvironment.IS_REACT_ACT_ENVIRONMENT;
  actEnvironment.IS_REACT_ACT_ENVIRONMENT = true;

  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);

  await act(async () => {
    root.render(element);
  });
  await act(async () => {
    await Promise.resolve();
  });

  return {
    html: () => container.innerHTML,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
      actEnvironment.IS_REACT_ACT_ENVIRONMENT = previousActEnvironment;
    },
  };
}
