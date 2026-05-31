import { Button, Empty, Select, Space, Spin, Table, Tag, Typography } from '@douyinfe/semi-ui';
import { IconChevronLeft, IconChevronRight, IconPlay } from '@douyinfe/semi-icons';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  LABELER_WORK_STATUS_LABELS,
  LABELER_WORK_STATUSES,
  type LabelerSessionWorkStatus,
  type Session,
} from '../../entities/submission/submissionTypes';
import { useMySessionsQuery } from '../../features/labeling/useMySessionsQuery';

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 10;

function parsePositiveInt(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parseWorkStatus(value: string | null): LabelerSessionWorkStatus | undefined {
  return LABELER_WORK_STATUSES.includes(value as LabelerSessionWorkStatus) ? (value as LabelerSessionWorkStatus) : undefined;
}

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(
        new Date(value),
      )
    : '—';
}

function statusColor(status: LabelerSessionWorkStatus) {
  if (status === 'in_progress') return 'blue';
  if (status === 'submitted') return 'teal';
  if (status === 'approved') return 'green';
  if (status === 'rejected') return 'red';
  if (status === 'returned_for_revision') return 'amber';
  return 'grey';
}

export function LabelerMySessionsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page'), DEFAULT_PAGE);
  const size = parsePositiveInt(searchParams.get('size'), DEFAULT_SIZE);
  const workStatus = parseWorkStatus(searchParams.get('workStatus'));
  const sessionsQuery = useMySessionsQuery({ page, size, workStatus });
  const data = sessionsQuery.data;
  const items = data?.items ?? [];
  const hasNext = page * size < (data?.total ?? 0);
  const summary = data?.summary ?? { submitted: 0, approved: 0, rejected: 0, returnedForRevision: 0 };

  const updateParams = (next: { page?: number; workStatus?: LabelerSessionWorkStatus | null }) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', String(next.page ?? page));
    params.set('size', String(size));
    if (next.workStatus === null) params.delete('workStatus');
    if (next.workStatus) params.set('workStatus', next.workStatus);
    setSearchParams(params);
  };

  const columns = useMemo(
    () => [
      {
        title: '任务',
        render: (_: unknown, record: Session) => (
          <div className="schema-title-cell">
            <Typography.Text strong>Task #{record.taskId}</Typography.Text>
            <Typography.Text type="tertiary">Session #{record.id}</Typography.Text>
          </div>
        ),
      },
      {
        title: 'Schema',
        width: 130,
        render: (_: unknown, record: Session) => <Tag color="blue">Schema #{record.schemaVersionId}</Tag>,
      },
      {
        title: '状态',
        width: 110,
        render: (_: unknown, record: Session) => (
          <Tag color={statusColor(record.workStatus)}>{LABELER_WORK_STATUS_LABELS[record.workStatus]}</Tag>
        ),
      },
      {
        title: '领取时间',
        width: 150,
        render: (_: unknown, record: Session) => formatDateTime(record.claimedAt),
      },
      {
        title: '提交时间',
        width: 150,
        render: (_: unknown, record: Session) => formatDateTime(record.submittedAt),
      },
      {
        title: '操作',
        width: 120,
        render: (_: unknown, record: Session) => (
          <Button icon={<IconPlay />} onClick={() => navigate(`/labeler/sessions/${record.id}`)}>
            {record.status === 'claimed' || record.status === 'returned_for_revision' ? '继续作答' : '查看作答'}
          </Button>
        ),
      },
    ],
    [navigate],
  );

  return (
    <section className="labeler-page" aria-label="Labeler sessions">
      <div className="page-heading">
        <div>
          <Typography.Title heading={3} className="page-title">
            我的数据
          </Typography.Title>
          <Typography.Text type="tertiary">查看已领取、审核中、通过、打回和待修改的作答会话。</Typography.Text>
        </div>
        <Select
          className="session-status-filter"
          value={workStatus ?? 'all'}
          onChange={(value) =>
            updateParams({ page: 1, workStatus: value === 'all' ? null : (value as LabelerSessionWorkStatus) })
          }
        >
          <Select.Option value="all">全部状态</Select.Option>
          {LABELER_WORK_STATUSES.map((item) => (
            <Select.Option key={item} value={item}>
              {LABELER_WORK_STATUS_LABELS[item]}
            </Select.Option>
          ))}
        </Select>
      </div>

      <div className="labeler-session-summary-grid" aria-label="我的数据统计">
        <button className="labeler-session-summary-card" type="button" onClick={() => updateParams({ page: 1, workStatus: 'submitted' })}>
          <span>已提交</span>
          <strong>{summary.submitted}</strong>
        </button>
        <button className="labeler-session-summary-card" type="button" onClick={() => updateParams({ page: 1, workStatus: 'approved' })}>
          <span>通过</span>
          <strong>{summary.approved}</strong>
        </button>
        <button className="labeler-session-summary-card" type="button" onClick={() => updateParams({ page: 1, workStatus: 'rejected' })}>
          <span>打回</span>
          <strong>{summary.rejected}</strong>
        </button>
        <button
          className="labeler-session-summary-card"
          type="button"
          onClick={() => updateParams({ page: 1, workStatus: 'returned_for_revision' })}
        >
          <span>待修改</span>
          <strong>{summary.returnedForRevision}</strong>
        </button>
      </div>

      <div className="task-toolbar">
        <Typography.Text type="tertiary">
          共 {data?.total ?? 0} 个会话
          {workStatus ? ` · ${LABELER_WORK_STATUS_LABELS[workStatus]}` : ''}
        </Typography.Text>
        <Space>
          <Button icon={<IconChevronLeft />} disabled={page <= 1} onClick={() => updateParams({ page: page - 1 })}>
            上一页
          </Button>
          <Typography.Text>第 {page} 页</Typography.Text>
          <Button icon={<IconChevronRight />} disabled={!hasNext} onClick={() => updateParams({ page: page + 1 })}>
            下一页
          </Button>
        </Space>
      </div>

      <div className="task-table-surface">
        {sessionsQuery.isLoading ? (
          <div className="task-state-panel">
            <Spin size="large" />
          </div>
        ) : null}
        {sessionsQuery.isError ? (
          <div className="task-state-panel">
            <Empty title="我的数据加载失败" description="请稍后重试。" />
          </div>
        ) : null}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && items.length === 0 ? (
          <div className="task-state-panel">
            <Empty title="暂无作答会话" description="去任务广场领取一个任务开始作答。" />
          </div>
        ) : null}
        {items.length > 0 ? <Table columns={columns} dataSource={items} rowKey="id" pagination={false} /> : null}
      </div>
    </section>
  );
}
