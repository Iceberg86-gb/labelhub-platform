import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { afterEach, describe, expect, it, vi } from 'vitest';

const reviewerQueueQueryMock = vi.hoisted(() => vi.fn());
const batchReviewMutationMock = vi.hoisted(() => vi.fn());
const renderSchemaQueryMock = vi.hoisted(() => vi.fn());
const verdictQueryMock = vi.hoisted(() => vi.fn());
const ledgerEntriesQueryMock = vi.hoisted(() => vi.fn());
const createLedgerEntryMutationMock = vi.hoisted(() => vi.fn());
const seniorReviewCasesQueryMock = vi.hoisted(() => vi.fn());
const markReviewDifficultyMutationMock = vi.hoisted(() => vi.fn());
const resolveSeniorReviewCaseMutationMock = vi.hoisted(() => vi.fn());
const userMock = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({
  navigate: vi.fn(),
  params: { submissionId: '501' },
  searchParams: new URLSearchParams(),
  setSearchParams: vi.fn(),
}));

function textFrom(value: ReactNode) {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
}

vi.mock('@douyinfe/semi-icons', () => ({
  IconArrowLeft: () => <span />,
  IconChevronLeft: () => <span />,
  IconChevronRight: () => <span />,
  IconClose: () => <span />,
  IconInfoCircle: () => <span />,
  IconPlay: () => <span />,
  IconPlusCircle: () => <span />,
  IconRefresh: () => <span />,
  IconTickCircle: () => <span />,
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
  Card: ({ children, className, title }: { children?: ReactNode; className?: string; title?: ReactNode }) => (
    <section className={className}>
      {title ? <h3>{title}</h3> : null}
      {children}
    </section>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Modal: ({ children, title }: { children?: ReactNode; title?: ReactNode }) => (
    <section>
      {title}
      {children}
    </section>
  ),
  Pagination: () => <nav />,
  Select: MockSelect,
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Spin: () => <div />,
  Table: ({ columns, dataSource }: { columns: Array<any>; dataSource: Array<any> }) => (
    <table data-column-aligns={columns.map((column) => column.align ?? 'left').join('|')}>
      <thead>
        <tr>
          {columns.map((column, index) => (
            <th key={column.dataIndex ?? index} data-align={column.align ?? 'left'}>
              {textFrom(column.title)}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {dataSource.map((record) => (
          <tr key={record.id}>
            {columns.map((column, index) => (
              <td key={column.dataIndex ?? index} data-align={column.align ?? 'left'}>
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
  TextArea: ({ className, placeholder, value }: { className?: string; placeholder?: string; value?: string }) => (
    <textarea className={className} placeholder={placeholder} value={value} readOnly />
  ),
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
  Tooltip: ({ children }: { children?: ReactNode }) => <>{children}</>,
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

vi.mock('../../features/quality/useReviewerQueueQuery', () => ({
  useReviewerQueueQuery: reviewerQueueQueryMock,
}));

vi.mock('../../features/quality/useBatchReviewMutation', () => ({
  useBatchReviewMutation: batchReviewMutationMock,
}));

vi.mock('../../features/labeling/useSubmissionRenderSchemaQuery', () => ({
  useSubmissionRenderSchemaQuery: renderSchemaQueryMock,
}));

vi.mock('../../features/quality/useSubmissionVerdictQuery', () => ({
  useSubmissionVerdictQuery: verdictQueryMock,
}));

vi.mock('../../features/quality/useLedgerEntriesQuery', () => ({
  useLedgerEntriesQuery: ledgerEntriesQueryMock,
}));

vi.mock('../../features/quality/useCreateLedgerEntryMutation', () => ({
  CreateLedgerEntryFailure: class CreateLedgerEntryFailure extends Error {
    status = 400;
    code = 'TEST';
    userMessage = '提交审核失败';
  },
  useCreateLedgerEntryMutation: createLedgerEntryMutationMock,
}));

vi.mock('../../features/quality/useSeniorReviewCasesQuery', () => ({
  useSeniorReviewCasesQuery: seniorReviewCasesQueryMock,
}));

vi.mock('../../features/quality/useMarkReviewDifficultyMutation', () => ({
  useMarkReviewDifficultyMutation: markReviewDifficultyMutationMock,
}));

vi.mock('../../features/quality/useResolveSeniorReviewCaseMutation', () => ({
  useResolveSeniorReviewCaseMutation: resolveSeniorReviewCaseMutationMock,
}));

vi.mock('../../features/labeling/formily/SchemaFormilyRenderer', () => ({
  SchemaFormilyRenderer: ({ itemPayload }: { itemPayload?: { prompt?: string } }) => (
    <section>
      历史作答
      {itemPayload?.prompt ? <span>{itemPayload.prompt}</span> : null}
    </section>
  ),
}));

vi.mock('./ReviewerAnswerSummary', () => ({
  ReviewerAnswerSummary: ({ itemPayload }: { itemPayload?: { prompt?: string } }) => (
    <section className="reviewer-answer-summary">
      平铺答案
      {itemPayload?.prompt ? <span>{itemPayload.prompt}</span> : null}
    </section>
  ),
}));

vi.mock('../../features/ai/AiProvenanceCard', () => ({
  AiProvenanceCard: ({ className }: { className?: string }) => (
    <section className={['ai-provenance-card', className].filter(Boolean).join(' ')}>AI 预审证据</section>
  ),
}));

vi.mock('../../shared/api/auth-storage', () => ({
  getUser: userMock,
}));

import { ReviewerQueuePage } from './ReviewerQueuePage';
import { ReviewerSubmissionPage } from './ReviewerSubmissionPage';

const queueSubmission = {
  id: 501,
  labelerId: 1002,
  reviewLevel: 'senior_reviewer',
  schemaName: '偏好对比标注',
  schemaVersionId: 77,
  schemaVersionNumber: 1,
  submittedAt: '2026-05-30T09:00:00Z',
  taskId: 22,
  taskTitle: '客服回复质检',
  verdict: { derivedFromEntryId: null, status: 'pending' },
};

const renderSchema = {
  answerPayload: { answer: '合规' },
  datasetItem: {
    itemPayload: {
      prompt: '请判断模型回答是否符合参考答案。',
    },
  },
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
};

const seniorCase = {
  accountability: null,
  caseType: 'arbitration',
  createdAt: '2026-06-08T17:00:00Z',
  id: 9001,
  priority: 'high',
  reason: null,
  resolution: null,
  schemaName: '偏好对比标注',
  schemaVersionNumber: 1,
  seniorReviewerId: null,
  sourceSignal: 'reviewer_difficulty',
  sourceSummary: 'Reviewer 标记疑难',
  status: 'open',
  submissionId: 501,
  taskId: 22,
  taskTitle: '客服回复质检',
};

afterEach(() => {
  reviewerQueueQueryMock.mockReset();
  batchReviewMutationMock.mockReset();
  renderSchemaQueryMock.mockReset();
  verdictQueryMock.mockReset();
  ledgerEntriesQueryMock.mockReset();
  createLedgerEntryMutationMock.mockReset();
  seniorReviewCasesQueryMock.mockReset();
  markReviewDifficultyMutationMock.mockReset();
  resolveSeniorReviewCaseMutationMock.mockReset();
  userMock.mockReset();
  routeState.navigate.mockReset();
  routeState.setSearchParams.mockReset();
  routeState.params = { submissionId: '501' };
  routeState.searchParams = new URLSearchParams();
});

describe('Reviewer pages design shell', () => {
  it('renders queue as a role-aware review operations desk', () => {
    userMock.mockReturnValue({ id: 1003, roles: ['REVIEWER', 'SENIOR_REVIEWER'] });
    reviewerQueueQueryMock.mockReturnValue({
      data: { items: [queueSubmission], total: 1 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    seniorReviewCasesQueryMock.mockReturnValue({
      data: { items: [], total: 0 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    batchReviewMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<ReviewerQueuePage />);

    expect(html).toContain('reviewer-queue-page reviewer-queue-page--workbench');
    expect(html).toContain('reviewer-workbench-hero');
    expect(html).toContain('reviewer-filter-strip');
    expect(html).toContain('reviewer-queue-flow-strip');
    expect(html).toContain('task-table-surface task-table-surface--reviewer');
    expect(html).toContain('reviewer-level-tag reviewer-level-tag--senior');
    expect(html).toContain('data-column-aligns="center|center|center|center|center|center|center|center"');
    expect(html).toContain('REVIEWER');
    expect(html).not.toContain('SENIOR REVIEWER');
    expect(html).toContain('提交 501');
    expect(html).toContain('客服回复质检');
    expect(html).toContain('偏好对比标注 v1');
    expect(html).not.toContain('Task #22');
    expect(html).not.toContain('Labeler #1002');
    expect(html).not.toContain('#77');
  });

  it('keeps the schema column non-empty while older queue responses are still missing readable schema metadata', () => {
    userMock.mockReturnValue({ id: 1003, roles: ['REVIEWER'] });
    reviewerQueueQueryMock.mockReturnValue({
      data: {
        items: [
          {
            ...queueSubmission,
            schemaName: undefined,
            schemaVersionNumber: undefined,
          },
        ],
        total: 1,
      },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    seniorReviewCasesQueryMock.mockReturnValue({
      data: { items: [], total: 0 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    batchReviewMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<ReviewerQueuePage />);

    expect(html).toContain('Schema 版本 77');
  });

  it('renders senior reviewer queue as case arbitration workbench', () => {
    userMock.mockReturnValue({ id: 1004, roles: ['REVIEWER', 'SENIOR_REVIEWER'] });
    routeState.searchParams = new URLSearchParams('reviewLevel=senior_reviewer');
    reviewerQueueQueryMock.mockReturnValue({
      data: { items: [], total: 0 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    seniorReviewCasesQueryMock.mockReturnValue({
      data: { items: [seniorCase], total: 1 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    batchReviewMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<ReviewerQueuePage />);

    expect(html).toContain('高级仲裁队列');
    expect(html).toContain('SENIOR REVIEWER');
    expect(html).not.toMatch(/>\s*REVIEWER\s*</);
    expect(html).toContain('共 1 个待处理 case');
    expect(html).toContain('客服回复质检');
    expect(html).toContain('偏好对比标注 v1');
    expect(html).toContain('疑难仲裁');
    expect(html).toContain('处理仲裁');
    expect(html).toContain('data-column-aligns="center|center|center|center|center|center"');
    expect(html).not.toContain('批量通过');
    expect(html).not.toContain('全部 Verdict');
  });

  it('renders submission detail as a three-zone reviewer workbench', () => {
    userMock.mockReturnValue({ id: 1003, roles: ['REVIEWER'] });
    routeState.searchParams = new URLSearchParams('reviewLevel=reviewer');
    reviewerQueueQueryMock.mockReturnValue({
      data: {
        items: [
          { ...queueSubmission, id: 501, reviewLevel: 'reviewer' },
          { ...queueSubmission, id: 502, reviewLevel: 'reviewer' },
        ],
        total: 2,
      },
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    seniorReviewCasesQueryMock.mockReturnValue({
      data: { items: [], total: 0 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    renderSchemaQueryMock.mockReturnValue({
      data: renderSchema,
      isError: false,
      isLoading: false,
    });
    verdictQueryMock.mockReturnValue({
      data: { derivedFromEntryId: null, status: 'pending' },
    });
    ledgerEntriesQueryMock.mockReturnValue({
      data: { items: [], total: 0 },
      isError: false,
      isLoading: false,
    });
    createLedgerEntryMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    markReviewDifficultyMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    resolveSeniorReviewCaseMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<ReviewerSubmissionPage />);

    expect(html).toContain('reviewer-submission-page reviewer-submission-page--decision');
    expect(html).toContain('reviewer-decision-hero');
    expect(html.indexOf('reviewer-ai-summary-band')).toBeLessThan(html.indexOf('reviewer-workbench-grid'));
    expect(html).toContain('reviewer-ai-summary-band__metrics');
    expect(html).toContain('reviewer-workbench-grid');
    expect(html).toContain('reviewer-reading-panel');
    expect(html).toContain('reviewer-workbench-rail');
    expect(html).toContain('reviewer-decision-rail');
    expect(html).toContain('reviewer-decision-rail__sticky');
    expect(html).toContain('reviewer-submission-queue-nav');
    expect(html).toContain('待审队列');
    expect(html).toContain('1/2');
    expect(html).toContain('下一条');
    expect(html).toContain('reviewer-human-decision-panel');
    expect(html).toContain('review-actions-card review-actions-card--primary');
    expect(html).toContain('review-reason-field review-reason-field--required');
    expect(html).toContain('reviewer-ai-layer reviewer-ai-layer--fields');
    expect(html).toContain('reviewer-ai-layer reviewer-ai-layer--debug');
    expect(html).toContain('reviewer-ai-layer reviewer-ai-layer--history');
    expect(html).toContain('AI 调用详情(调试)');
    expect(html).toContain('ai-provenance-card ai-provenance-card--assistive');
    expect(html).toContain('审核历史');
    expect(html).toContain('<summary>审核历史</summary>');
    expect(html).toContain('人工最终裁决');
    expect(html).toContain('平铺答案');
    expect(html).toContain('请判断模型回答是否符合参考答案。');
  });

  it('renders AI recommendation separately from the human final verdict', () => {
    userMock.mockReturnValue({ id: 1003, roles: ['REVIEWER'] });
    routeState.searchParams = new URLSearchParams('reviewLevel=reviewer');
    reviewerQueueQueryMock.mockReturnValue({
      data: { items: [{ ...queueSubmission, id: 501, reviewLevel: 'reviewer' }], total: 1 },
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    seniorReviewCasesQueryMock.mockReturnValue({
      data: { items: [], total: 0 },
      error: null,
      isError: false,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    renderSchemaQueryMock.mockReturnValue({
      data: renderSchema,
      isError: false,
      isLoading: false,
    });
    verdictQueryMock.mockReturnValue({
      data: { derivedAt: '2026-06-02T22:13:00Z', derivedFromEntryId: 26, status: 'approved' },
    });
    ledgerEntriesQueryMock.mockReturnValue({
      data: {
        items: [
          {
            actorType: 'ai_agent',
            actorUserId: null,
            aiCallId: 6,
            createdAt: '2026-06-02T21:30:00Z',
            entryType: 'ai_overall_recommendation',
            id: 24,
            payload: {
              dimensionScores: [
                { dimension: 'relevance', reason: '回答切题', score: '0.92' },
                { dimension: 'accuracy', reason: '答案匹配参考结论', score: '0.97' },
                { dimension: 'format', reason: '表达清楚', score: '0.94' },
                { dimension: 'safety', reason: '无风险内容', score: '0.93' },
              ],
              finalScore: '0.95',
              passThreshold: '0.80',
              recommendation: 'pass',
              rejectThreshold: '0.40',
              scoringRuleVersion: 'qa-v1',
              summary: '模型回答覆盖关键答案。',
            },
            submissionId: 501,
            taskId: 22,
          },
          {
            actorType: 'reviewer',
            actorUserId: 1003,
            aiCallId: null,
            createdAt: '2026-06-02T22:10:00Z',
            entryType: 'reviewer_overall_verdict',
            id: 26,
            payload: { reason: '人工复核通过', reviewLevel: 'senior_reviewer', verdict: 'approve' },
            submissionId: 501,
            taskId: 22,
          },
          {
            actorType: 'ai_agent',
            actorUserId: null,
            aiCallId: 6,
            createdAt: '2026-06-02T21:31:00Z',
            entryType: 'ai_field_finding',
            id: 25,
            payload: {
              confidence: '0.91',
              fieldPath: 'answer',
              finding: '回答覆盖了主要结论。',
              label: '结论',
              severity: 'info',
            },
            submissionId: 501,
            taskId: 22,
          },
        ],
        total: 3,
      },
      isError: false,
      isLoading: false,
    });
    createLedgerEntryMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    markReviewDifficultyMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    resolveSeniorReviewCaseMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });

    const html = renderToString(<ReviewerSubmissionPage />);

    expect(html).toContain('AI 综合判定（建议）');
    expect(html).toContain('reviewer-ai-summary-band');
    expect(html).toContain('reviewer-ai-recommendation-line');
    expect(html).toContain('reviewer-ai-dimension-mini-bars');
    expect(html).toContain('reviewer-ai-finding-list reviewer-ai-finding-list--compact');
    expect(html).toContain('reviewer-ai-layer reviewer-ai-layer--history');
    expect(html).toContain('ledger-entry-timeline');
    expect(html).toContain('非最终裁决');
    expect(html).toContain('AI 建议');
    expect(html).toContain('0.95');
    expect(html).toContain('通过阈值');
    expect(html).toContain('0.80');
    expect(html).toContain('相关性(relevance)');
    expect(html).toContain('准确性(accuracy)');
    expect(html).toContain('表达与格式(format)');
    expect(html).toContain('安全性(safety)');
    expect(html).toContain('answer');
    expect(html).toContain('回答覆盖了主要结论。');
    expect(html).toContain('人工最终裁决');
    expect(html).toContain('最终裁决来源');
    expect(html).toContain('已通过');
  });
});
